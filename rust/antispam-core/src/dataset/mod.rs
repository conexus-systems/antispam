use crate::error::{AntispamError, Result};
use crate::phone::{get_shard_id, NumberKind};
use crate::reputation::Category;
use chrono::{DateTime, Utc};
use flate2::read::GzDecoder;
use flate2::write::GzEncoder;
use flate2::Compression;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::io::{Read, Write};

/// Manifest for dataset
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Manifest {
    pub schema_version: u8,
    pub dataset: String,
    pub version: u64,
    pub created_at: DateTime<Utc>,
    pub expires_at: DateTime<Utc>,
    pub key_id: String,
    pub compression: String,
    pub record_count: u64,
    pub publication_policy: PublicationPolicy,
    pub shards: Vec<ShardInfo>,
    pub deltas: Vec<DeltaInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PublicationPolicy {
    pub min_weighted_reporters: f64,
    pub min_age_hours: f64,
    pub min_score: u8,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ShardInfo {
    pub id: String,
    pub path: String,
    pub version: u64,
    pub sha256: String,
    pub size: u64,
    pub record_count: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DeltaInfo {
    pub shard: String,
    pub from_version: u64,
    pub to_version: u64,
    pub path: String,
    pub sha256: String,
    pub size: u64,
    pub record_count: u64,
}

/// Binary record (16 bytes)
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Record {
    pub number: u64,
    pub score: u8,
    pub category: u8,
    pub confidence: u8,
    pub flags: u8,
    pub reporters: u16,
    pub last_seen_day: u16,
}

impl Record {
    pub const SIZE: usize = 16;

    pub fn to_bytes(&self) -> [u8; 16] {
        let mut bytes = [0u8; 16];
        bytes[0..8].copy_from_slice(&self.number.to_be_bytes());
        bytes[8] = self.score;
        bytes[9] = self.category;
        bytes[10] = self.confidence;
        bytes[11] = self.flags;
        bytes[12..14].copy_from_slice(&self.reporters.to_be_bytes());
        bytes[14..16].copy_from_slice(&self.last_seen_day.to_be_bytes());
        bytes
    }

    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        if bytes.len() != 16 {
            return Err(AntispamError::InvalidBinaryFormat("Record must be 16 bytes".to_string()));
        }
        let number = u64::from_be_bytes(bytes[0..8].try_into().unwrap());
        let score = bytes[8];
        let category = bytes[9];
        let confidence = bytes[10];
        let flags = bytes[11];
        let reporters = u16::from_be_bytes(bytes[12..14].try_into().unwrap());
        let last_seen_day = u16::from_be_bytes(bytes[14..16].try_into().unwrap());
        Ok(Self {
            number,
            score,
            category,
            confidence,
            flags,
            reporters,
            last_seen_day,
        })
    }

    pub fn is_tombstone(&self) -> bool {
        self.flags & 0x01 != 0
    }

    pub fn is_campaign(&self) -> bool {
        self.flags & 0x02 != 0
    }

    pub fn is_disputed(&self) -> bool {
        self.flags & 0x04 != 0
    }

    pub fn is_verified_org(&self) -> bool {
        self.flags & 0x08 != 0
    }
}

/// Binary file header (32 bytes)
#[derive(Debug, Clone)]
pub struct Header {
    pub magic: [u8; 4],
    pub format_version: u8,
    pub kind: u8,
    pub reserved1: u16,
    pub record_count: u32,
    pub reserved2: u32,
    pub version: u64,
    pub from_version: u64,
}

impl Header {
    pub const SIZE: usize = 32;
    pub const MAGIC: [u8; 4] = *b"ASBR";
    pub const FORMAT_VERSION: u8 = 1;
    pub const KIND_FULL: u8 = 0;
    pub const KIND_DELTA: u8 = 1;

    pub fn new(kind: u8, record_count: u32, version: u64, from_version: u64) -> Self {
        Self {
            magic: Self::MAGIC,
            format_version: Self::FORMAT_VERSION,
            kind,
            reserved1: 0,
            record_count,
            reserved2: 0,
            version,
            from_version,
        }
    }

    pub fn to_bytes(&self) -> [u8; 32] {
        let mut bytes = [0u8; 32];
        bytes[0..4].copy_from_slice(&self.magic);
        bytes[4] = self.format_version;
        bytes[5] = self.kind;
        bytes[6..8].copy_from_slice(&self.reserved1.to_be_bytes());
        bytes[8..12].copy_from_slice(&self.record_count.to_be_bytes());
        bytes[12..16].copy_from_slice(&self.reserved2.to_be_bytes());
        bytes[16..24].copy_from_slice(&self.version.to_be_bytes());
        bytes[24..32].copy_from_slice(&self.from_version.to_be_bytes());
        bytes
    }

    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        if bytes.len() != 32 {
            return Err(AntispamError::InvalidBinaryFormat("Header must be 32 bytes".to_string()));
        }
        if &bytes[0..4] != Self::MAGIC {
            return Err(AntispamError::InvalidBinaryFormat("Invalid magic".to_string()));
        }
        if bytes[4] != Self::FORMAT_VERSION {
            return Err(AntispamError::InvalidBinaryFormat("Unsupported format version".to_string()));
        }
        Ok(Self {
            magic: bytes[0..4].try_into().unwrap(),
            format_version: bytes[4],
            kind: bytes[5],
            reserved1: u16::from_be_bytes(bytes[6..8].try_into().unwrap()),
            record_count: u32::from_be_bytes(bytes[8..12].try_into().unwrap()),
            reserved2: u32::from_be_bytes(bytes[12..16].try_into().unwrap()),
            version: u64::from_be_bytes(bytes[16..24].try_into().unwrap()),
            from_version: u64::from_be_bytes(bytes[24..32].try_into().unwrap()),
        })
    }
}

/// Shard file reader (memory-mapped compatible)
pub struct ShardReader {
    data: Vec<u8>,
    header: Header,
    records_offset: usize,
}

impl ShardReader {
    pub fn open(data: &[u8]) -> Result<Self> {
        if data.len() < Header::SIZE {
            return Err(AntispamError::InvalidBinaryFormat("File too small for header".to_string()));
        }
        let header = Header::from_bytes(&data[..Header::SIZE])?;
        let expected_size = Header::SIZE + header.record_count as usize * Record::SIZE;
        if data.len() < expected_size {
            return Err(AntispamError::InvalidBinaryFormat("File too small for records".to_string()));
        }
        Ok(Self {
            data: data.to_vec(),
            header,
            records_offset: Header::SIZE,
        })
    }

    pub fn header(&self) -> &Header {
        &self.header
    }

    pub fn record_count(&self) -> u32 {
        self.header.record_count
    }

    pub fn get(&self, index: usize) -> Option<Record> {
        if index >= self.header.record_count as usize {
            return None;
        }
        let offset = self.records_offset + index * Record::SIZE;
        Record::from_bytes(&self.data[offset..offset + Record::SIZE]).ok()
    }

    /// Binary search for number (records are sorted by number)
    pub fn find(&self, number: u64) -> Option<Record> {
        let mut low = 0;
        let mut high = self.header.record_count as usize;
        while low < high {
            let mid = (low + high) / 2;
            let record = self.get(mid)?;
            if record.number < number {
                low = mid + 1;
            } else if record.number > number {
                high = mid;
            } else {
                return Some(record);
            }
        }
        None
    }

    pub fn iter(&self) -> ShardIter<'_> {
        ShardIter { reader: self, index: 0 }
    }
}

pub struct ShardIter<'a> {
    reader: &'a ShardReader,
    index: usize,
}

impl<'a> Iterator for ShardIter<'a> {
    type Item = Record;

    fn next(&mut self) -> Option<Self::Item> {
        let record = self.reader.get(self.index)?;
        self.index += 1;
        Some(record)
    }
}

/// Shard writer
pub struct ShardWriter {
    records: Vec<Record>,
}

impl ShardWriter {
    pub fn new() -> Self {
        Self { records: Vec::new() }
    }

    pub fn add(&mut self, record: Record) -> Result<()> {
        if !self.records.is_empty() {
            let last = self.records.last().unwrap();
            if record.number <= last.number {
                return Err(AntispamError::InvalidBinaryFormat("Records must be strictly sorted by number".to_string()));
            }
        }
        self.records.push(record);
        Ok(())
    }

    pub fn build(self, version: u64, from_version: u64) -> Result<Vec<u8>> {
        let mut records = self.records;
        records.sort_by_key(|r| r.number);

        for i in 1..records.len() {
            if records[i].number <= records[i - 1].number {
                return Err(AntispamError::InvalidBinaryFormat("Records must be strictly sorted".to_string()));
            }
        }

        let header = Header::new(Header::KIND_FULL, records.len() as u32, version, from_version);
        let mut data = Vec::with_capacity(Header::SIZE + records.len() * Record::SIZE);
        data.extend_from_slice(&header.to_bytes());
        for record in records {
            data.extend_from_slice(&record.to_bytes());
        }
        Ok(data)
    }

    pub fn build_gzipped(self, version: u64, from_version: u64) -> Result<Vec<u8>> {
        let uncompressed = self.build(version, from_version)?;
        let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
        encoder.write_all(&uncompressed)?;
        Ok(encoder.finish()?)
    }
}

impl Default for ShardWriter {
    fn default() -> Self {
        Self::new()
    }
}

/// Delta writer (for incremental updates)
pub struct DeltaWriter {
    records: Vec<Record>,
    from_version: u64,
    to_version: u64,
}

impl DeltaWriter {
    pub fn new(from_version: u64, to_version: u64) -> Self {
        Self {
            records: Vec::new(),
            from_version,
            to_version,
        }
    }

    pub fn add(&mut self, record: Record) -> Result<()> {
        if !self.records.is_empty() {
            let last = self.records.last().unwrap();
            if record.number <= last.number {
                return Err(AntispamError::InvalidBinaryFormat("Delta records must be strictly sorted".to_string()));
            }
        }
        self.records.push(record);
        Ok(())
    }

    pub fn build_gzipped(self) -> Result<Vec<u8>> {
        let mut records = self.records;
        records.sort_by_key(|r| r.number);

        let header = Header::new(Header::KIND_DELTA, records.len() as u32, self.to_version, self.from_version);
        let mut data = Vec::with_capacity(Header::SIZE + records.len() * Record::SIZE);
        data.extend_from_slice(&header.to_bytes());
        for record in records {
            data.extend_from_slice(&record.to_bytes());
        }

        let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
        encoder.write_all(&data)?;
        Ok(encoder.finish()?)
    }
}

/// Verify gzipped file SHA256 and size
pub fn verify_gzipped(data: &[u8], expected_sha256: &str, expected_size: u64) -> Result<()> {
    if data.len() as u64 != expected_size {
        return Err(AntispamError::ChecksumMismatch {
            expected: expected_size.to_string(),
            actual: data.len().to_string(),
        });
    }
    let mut hasher = Sha256::new();
    hasher.update(data);
    let actual = hex::encode(hasher.finalize());
    if actual != expected_sha256 {
        return Err(AntispamError::ChecksumMismatch {
            expected: expected_sha256.to_string(),
            actual,
        });
    }
    Ok(())
}

/// Decompress gzipped data
pub fn decompress_gzip(data: &[u8]) -> Result<Vec<u8>> {
    let mut decoder = GzDecoder::new(data);
    let mut decompressed = Vec::new();
    decoder.read_to_end(&mut decompressed)?;
    Ok(decompressed)
}

/// Convert Category to dataset category code
pub fn category_to_code(category: Category) -> u8 {
    match category {
        Category::Other => 0,
        Category::Telemarketing => 1,
        Category::Robocall => 2,
        Category::SilentCall => 3,
        Category::Collection => 4,
        Category::BankScam => 5,
        Category::PixScam => 6,
        Category::Phishing => 7,
        Category::DeliveryScam => 8,
        Category::FakeSupport => 9,
        Category::Loan => 10,
        Category::Survey => 11,
        Category::Spoofing => 12,
        Category::Legitimate => 13,
    }
}

/// Convert dataset category code to Category
pub fn code_to_category(code: u8) -> Category {
    match code {
        0 => Category::Other,
        1 => Category::Telemarketing,
        2 => Category::Robocall,
        3 => Category::SilentCall,
        4 => Category::Collection,
        5 => Category::BankScam,
        6 => Category::PixScam,
        7 => Category::Phishing,
        8 => Category::DeliveryScam,
        9 => Category::FakeSupport,
        10 => Category::Loan,
        11 => Category::Survey,
        12 => Category::Spoofing,
        13 => Category::Legitimate,
        _ => Category::Other,
    }
}

/// Convert NumberKind to shard ID
pub fn numberkind_to_shard(kind: NumberKind, ddd: Option<&str>) -> String {
    match kind {
        NumberKind::Mobile | NumberKind::Landline => {
            format!("55-{}", ddd.unwrap_or("ng"))
        }
        NumberKind::International => "55-intl".to_string(),
        _ => "55-ng".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_record_roundtrip() {
        let record = Record {
            number: 5511987654321,
            score: 85,
            category: 1,
            confidence: 90,
            flags: 0,
            reporters: 100,
            last_seen_day: 2500,
        };
        let bytes = record.to_bytes();
        let decoded = Record::from_bytes(&bytes).unwrap();
        assert_eq!(record, decoded);
    }

    #[test]
    fn test_header_roundtrip() {
        let header = Header::new(Header::KIND_FULL, 100, 2026100700, 0);
        let bytes = header.to_bytes();
        let decoded = Header::from_bytes(&bytes).unwrap();
        assert_eq!(header.magic, decoded.magic);
        assert_eq!(header.record_count, decoded.record_count);
        assert_eq!(header.version, decoded.version);
    }

    #[test]
    fn test_shard_writer() {
        let mut writer = ShardWriter::new();
        writer.add(Record {
            number: 5511987654321,
            score: 85,
            category: 1,
            confidence: 90,
            flags: 0,
            reporters: 100,
            last_seen_day: 2500,
        }).unwrap();
        writer.add(Record {
            number: 5511987654322,
            score: 70,
            category: 2,
            confidence: 80,
            flags: 0,
            reporters: 50,
            last_seen_day: 2499,
        }).unwrap();

        let data = writer.build(2026100700, 0).unwrap();
        let reader = ShardReader::open(&data).unwrap();
        assert_eq!(reader.record_count(), 2);

        let r0 = reader.get(0).unwrap();
        assert_eq!(r0.number, 5511987654321);
        assert_eq!(r0.score, 85);

        let r1 = reader.get(1).unwrap();
        assert_eq!(r1.number, 5511987654322);
        assert_eq!(r1.score, 70);

        let found = reader.find(5511987654321).unwrap();
        assert_eq!(found.number, 5511987654321);
        assert!(reader.find(5511987654323).is_none());
    }

    #[test]
    fn test_delta_writer() {
        let mut writer = DeltaWriter::new(2026100600, 2026100700);
        writer.add(Record {
            number: 5511987654321,
            score: 90,
            category: 1,
            confidence: 95,
            flags: 0,
            reporters: 150,
            last_seen_day: 2501,
        }).unwrap();
        writer.add(Record {
            number: 5511987654399,
            score: 0,
            category: 0,
            confidence: 0,
            flags: 0x01,
            reporters: 0,
            last_seen_day: 0,
        }).unwrap();

        let data = writer.build_gzipped().unwrap();
        let decompressed = decompress_gzip(&data).unwrap();
        let reader = ShardReader::open(&decompressed).unwrap();
        assert_eq!(reader.header().kind, Header::KIND_DELTA);
        assert_eq!(reader.header().from_version, 2026100600);
        assert_eq!(reader.header().version, 2026100700);
    }

    #[test]
    fn test_gzip_roundtrip() {
        let mut writer = ShardWriter::new();
        writer.add(Record {
            number: 5511987654321,
            score: 85,
            category: 1,
            confidence: 90,
            flags: 0,
            reporters: 100,
            last_seen_day: 2500,
        }).unwrap();

        let gzipped = writer.build_gzipped(2026100700, 0).unwrap();
        let decompressed = decompress_gzip(&gzipped).unwrap();
        let reader = ShardReader::open(&decompressed).unwrap();
        assert_eq!(reader.record_count(), 1);
    }

    #[test]
    fn test_category_codes() {
        assert_eq!(category_to_code(Category::Telemarketing), 1);
        assert_eq!(category_to_code(Category::BankScam), 5);
        assert_eq!(code_to_category(1), Category::Telemarketing);
        assert_eq!(code_to_category(13), Category::Legitimate);
        assert_eq!(code_to_category(99), Category::Other);
    }

    #[test]
    fn test_shard_id() {
        assert_eq!(numberkind_to_shard(NumberKind::Mobile, Some("11")), "55-11");
        assert_eq!(numberkind_to_shard(NumberKind::International, None), "55-intl");
        assert_eq!(numberkind_to_shard(NumberKind::Telemarketing, None), "55-ng");
    }
}