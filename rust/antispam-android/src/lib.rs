use antispam_core::{
    decision::{CallAction, CallContext, CallPresentation, CallDecision, DecisionEngine, DecisionEngineConfig, EngineInput, ProtectionMode, Rule, RuleMatch, StirShaken},
    phone::{normalize, NormalizedNumber, NumberKind, e164_to_key},
    dataset::{ShardReader, Record, Header, verify_gzipped, decompress_gzip, category_to_code, code_to_category},
    reputation::{compute_reputation, ReputationConfig, ReportInput, NumberContext},
    crypto::KeyStore,
    error::AntispamError,
};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use jni::{
    objects::{JClass, JObject, JString, JByteArray, JValue, JMethodID},
    sys::{jbyteArray, jint, jlong, jboolean, jshort, jbyte, jvalue},
    JNIEnv,
    signature::Primitive,
};
use lazy_static::lazy_static;
use serde_json;
use std::collections::{HashSet, HashMap};
use std::sync::Mutex;

lazy_static! {
    static ref KEY_STORE: Mutex<KeyStore> = Mutex::new(KeyStore::new());
    static ref DECISION_ENGINE: Mutex<DecisionEngine> = Mutex::new(DecisionEngine::new());
}

/// Initialize the native library
#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: jni::JavaVM, _reserved: *mut std::os::raw::c_void) -> jint {
    jni::sys::JNI_VERSION_1_6
}

/// Initialize key store with embedded keys
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_crypto_NativeCrypto_initKeyStore<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    keys_json: JString<'local>,
) -> jboolean {
    let keys_json: String = match env.get_string(&keys_json) {
        Ok(s) => s.into(),
        Err(_) => return jni::sys::JNI_FALSE,
    };

    let mut store = KEY_STORE.lock().unwrap();
    match antispam_core::crypto::parse_test_keys(&keys_json) {
        Ok(keys) => {
            for (key_id, key_bytes) in keys {
                if let Err(_) = store.add_key(key_id, &BASE64.encode(key_bytes)) {
                    return jni::sys::JNI_FALSE;
                }
            }
            jni::sys::JNI_TRUE
        }
        Err(_) => jni::sys::JNI_FALSE,
    }
}

/// Verify manifest signature
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_crypto_NativeCrypto_verifyManifest<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    manifest_bytes: JByteArray<'local>,
    key_id: JString<'local>,
    signature_b64: JString<'local>,
) -> jboolean {
    let manifest_bytes = match env.convert_byte_array(manifest_bytes) {
        Ok(b) => b,
        Err(_) => return jni::sys::JNI_FALSE,
    };

    let key_id: String = match env.get_string(&key_id) {
        Ok(s) => s.into(),
        Err(_) => return jni::sys::JNI_FALSE,
    };

    let signature_b64: String = match env.get_string(&signature_b64) {
        Ok(s) => s.into(),
        Err(_) => return jni::sys::JNI_FALSE,
    };

    let store = KEY_STORE.lock().unwrap();
    match store.verify_manifest(&manifest_bytes, &key_id, &signature_b64) {
        Ok(valid) => if valid { jni::sys::JNI_TRUE } else { jni::sys::JNI_FALSE },
        Err(_) => jni::sys::JNI_FALSE,
    }
}

/// Normalize phone number
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_phone_NativePhone_normalize<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    raw: JString<'local>,
    user_ddd: JString<'local>,
) -> JObject<'local> {
    let raw: String = match env.get_string(&raw) {
        Ok(s) => s.into(),
        Err(_) => return JObject::null(),
    };

    let user_ddd: String = match env.get_string(&user_ddd) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let user_ddd_opt = if user_ddd.is_empty() { None } else { Some(user_ddd.as_str()) };
    let normalized = normalize(Some(&raw), user_ddd_opt);

    let class = match env.find_class("com/conexus/antispam/phone/NormalizedNumber") {
        Ok(c) => c,
        Err(_) => return JObject::null(),
    };

    let constructor = match env.get_method_id(&class, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V") {
        Ok(c) => c,
        Err(_) => return JObject::null(),
    };

    let input = env.new_string(&normalized.input).unwrap();
    let kind = env.new_string(&normalized.kind.as_str()).unwrap();
    let e164 = env.new_string(&normalized.e164.unwrap_or_default()).unwrap();
    let ddd = env.new_string(&normalized.ddd.unwrap_or_default()).unwrap();
    let shard = env.new_string(&normalized.shard.unwrap_or_default()).unwrap();

    let args: [jvalue; 5] = [
        jvalue { l: input.into_raw() },
        jvalue { l: kind.into_raw() },
        jvalue { l: e164.into_raw() },
        jvalue { l: ddd.into_raw() },
        jvalue { l: shard.into_raw() },
    ];

    // Use new_object_unchecked to avoid trait inference issues
    match unsafe { env.new_object_unchecked(class, constructor, &args) } {
        Ok(obj) => obj,
        Err(_) => JObject::null(),
    }
}

/// Get shard ID for E.164 number
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_phone_NativePhone_getShardId<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    e164: JString<'local>,
) -> JString<'local> {
    let e164: String = match env.get_string(&e164) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap(),
    };

    let shard = antispam_core::phone::get_shard_id(&e164).unwrap_or_default();
    env.new_string(&shard).unwrap_or_default()
}

/// Convert E.164 to u64 key
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_phone_NativePhone_e164ToKey<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    e164: JString<'local>,
) -> jlong {
    let e164: String = match env.get_string(&e164) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    match e164_to_key(&e164) {
        Ok(key) => key as jlong,
        Err(_) => 0,
    }
}

/// Make a spam decision
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_decision_NativeDecision_decide<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    input_json: JString<'local>,
) -> JString<'local> {
    let input_json: String = match env.get_string(&input_json) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap(),
    };

    let engine = DECISION_ENGINE.lock().unwrap();
    let input: EngineInput = match serde_json::from_str(&input_json) {
        Ok(i) => i,
        Err(_) => return env.new_string("").unwrap(),
    };

    let decision = engine.decide(input);
    let output = match serde_json::to_string(&decision) {
        Ok(s) => s,
        Err(_) => return env.new_string("").unwrap(),
    };

    env.new_string(&output).unwrap_or_default()
}

/// Open and verify shard file
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_dataset_NativeDataset_verifyShard<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    shard_bytes: JByteArray<'local>,
    expected_sha256: JString<'local>,
    expected_size: jlong,
) -> jboolean {
    let shard_bytes = match env.convert_byte_array(shard_bytes) {
        Ok(b) => b,
        Err(_) => return jni::sys::JNI_FALSE,
    };

    let expected_sha256: String = match env.get_string(&expected_sha256) {
        Ok(s) => s.into(),
        Err(_) => return jni::sys::JNI_FALSE,
    };

    match verify_gzipped(&shard_bytes, &expected_sha256, expected_size as u64) {
        Ok(()) => jni::sys::JNI_TRUE,
        Err(_) => jni::sys::JNI_FALSE,
    }
}

/// Decompress gzipped shard
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_dataset_NativeDataset_decompressShard<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    shard_bytes: JByteArray<'local>,
) -> JByteArray<'local> {
    let shard_bytes = match env.convert_byte_array(shard_bytes) {
        Ok(b) => b,
        Err(_) => return JByteArray::default(),
    };

    match decompress_gzip(&shard_bytes) {
        Ok(decompressed) => env.byte_array_from_slice(&decompressed).unwrap_or_default(),
        Err(_) => JByteArray::default(),
    }
}

/// Open shard reader and find number
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_dataset_NativeDataset_findInShard<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    shard_bytes: JByteArray<'local>,
    number: jlong,
) -> JObject<'local> {
    let shard_bytes = match env.convert_byte_array(shard_bytes) {
        Ok(b) => b,
        Err(_) => return JObject::null(),
    };

    let reader = match ShardReader::open(&shard_bytes) {
        Ok(r) => r,
        Err(_) => return JObject::null(),
    };

    let record = match reader.find(number as u64) {
        Some(r) => r,
        None => return JObject::null(),
    };

    let class = match env.find_class("com/conexus/antispam/dataset/ShardRecord") {
        Ok(c) => c,
        Err(_) => return JObject::null(),
    };

    let constructor = match env.get_method_id(&class, "<init>", "(JBBBBBSS)V") {
        Ok(c) => c,
        Err(_) => return JObject::null(),
    };

    let obj = unsafe { env.new_object_unchecked(
        class,
        constructor,
        &[
            jvalue { j: record.number as i64 },
            jvalue { b: record.score as i8 },
            jvalue { b: record.category as i8 },
            jvalue { b: record.confidence as i8 },
            jvalue { b: record.flags as i8 },
            jvalue { b: 0 }, // reserved
            jvalue { s: record.reporters as i16 },
            jvalue { s: record.last_seen_day as i16 },
        ],
    ) };

    match obj {
        Ok(obj) => obj,
        Err(_) => JObject::null(),
    }
}

/// Get record count in shard
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_dataset_NativeDataset_getRecordCount(
    mut env: JNIEnv,
    _class: JClass,
    shard_bytes: JByteArray,
) -> jint {
    let shard_bytes = match env.convert_byte_array(shard_bytes) {
        Ok(b) => b,
        Err(_) => return -1,
    };

    match ShardReader::open(&shard_bytes) {
        Ok(reader) => reader.record_count() as jint,
        Err(_) => -1,
    }
}

/// Verify delta and apply to shard
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_dataset_NativeDataset_applyDelta<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    shard_bytes: JByteArray<'local>,
    delta_bytes: JByteArray<'local>,
) -> JByteArray<'local> {
    let shard_bytes = match env.convert_byte_array(shard_bytes) {
        Ok(b) => b,
        Err(_) => return JByteArray::default(),
    };

    let delta_bytes = match env.convert_byte_array(delta_bytes) {
        Ok(b) => b,
        Err(_) => return JByteArray::default(),
    };

    // Decompress both
    let shard_data = match decompress_gzip(&shard_bytes) {
        Ok(d) => d,
        Err(_) => return JByteArray::default(),
    };

    let delta_data = match decompress_gzip(&delta_bytes) {
        Ok(d) => d,
        Err(_) => return JByteArray::default(),
    };

    // Read shard
    let mut shard_reader = match ShardReader::open(&shard_data) {
        Ok(r) => r,
        Err(_) => return JByteArray::default(),
    };

    // Read delta
    let delta_reader = match ShardReader::open(&delta_data) {
        Ok(r) => r,
        Err(_) => return JByteArray::default(),
    };

    // Verify delta version matches
    if delta_reader.header().from_version != shard_reader.header().version {
        return JByteArray::default();
    }

    // Apply delta - merge records
    let mut records: Vec<Record> = shard_reader.iter().collect();
    
    for delta_record in delta_reader.iter() {
        if delta_record.is_tombstone() {
            // Remove record
            records.retain(|r| r.number != delta_record.number);
        } else {
            // Update or insert
            if let Some(pos) = records.iter().position(|r| r.number == delta_record.number) {
                records[pos] = delta_record;
            } else {
                // Insert maintaining sort order
                let insert_pos = records.binary_search_by_key(&delta_record.number, |r| r.number)
                    .unwrap_or_else(|e| e);
                records.insert(insert_pos, delta_record);
            }
        }
    }

    // Build new shard
    let mut writer = antispam_core::dataset::ShardWriter::new();
    for record in records {
        if let Err(_) = writer.add(record) {
            return JByteArray::default();
        }
    }

    let new_shard = match writer.build_gzipped(delta_reader.header().version, 0) {
        Ok(s) => s,
        Err(_) => return JByteArray::default(),
    };

    env.byte_array_from_slice(&new_shard).unwrap_or_default()
}

/// Compute reputation
#[no_mangle]
pub extern "system" fn Java_com_conexus_antispam_reputation_NativeReputation_compute<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    reports_json: JString<'local>,
    now: jlong,
    ctx_json: JString<'local>,
    config_json: JString<'local>,
) -> JString<'local> {
    let reports_json: String = match env.get_string(&reports_json) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap(),
    };

    let ctx_json: String = match env.get_string(&ctx_json) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap(),
    };

    let config_json: String = match env.get_string(&config_json) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap(),
    };

    let reports: Vec<ReportInput> = match serde_json::from_str(&reports_json) {
        Ok(r) => r,
        Err(_) => return env.new_string("").unwrap(),
    };

    let ctx: NumberContext = match serde_json::from_str(&ctx_json) {
        Ok(c) => c,
        Err(_) => return env.new_string("").unwrap(),
    };

    let config: ReputationConfig = match serde_json::from_str(&config_json) {
        Ok(c) => c,
        Err(_) => return env.new_string("").unwrap(),
    };

    let reputation = compute_reputation(&reports, now as i64, &ctx, &config);
    let output = match serde_json::to_string(&reputation) {
        Ok(s) => s,
        Err(_) => return env.new_string("").unwrap(),
    };

    env.new_string(&output).unwrap_or_default()
}