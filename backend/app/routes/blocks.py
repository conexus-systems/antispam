"""
User blocklist sync endpoints (requires Conta Conexus auth).
"""
from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession
from pydantic import BaseModel, Field
from datetime import datetime
from typing import List, Optional
from app.database import get_db
from app.models import UserBlocklist, BlockAction, PhoneHash
import structlog

logger = structlog.get_logger()
router = APIRouter()


class BlocklistItem(BaseModel):
    id: int
    phone_hash: str
    action: BlockAction
    source: str
    created_at: str


class BlocklistCreate(BaseModel):
    phone_hash: str = Field(..., min_length=16, max_length=16)
    action: BlockAction
    source: str = "remote"


class BlocklistResponse(BaseModel):
    items: List[BlocklistItem]
    total: int


@router.get("/blocks", response_model=BlocklistResponse)
async def list_blocks(
    user_id: str = Header(..., alias="X-User-ID"),
    action: Optional[BlockAction] = None,
    limit: int = 100,
    offset: int = 0,
    db: AsyncSession = Depends(get_db)
):
    """
    Listar bloqueios/permissões do usuário.
    
    Header obrigatório:
    - **X-User-ID**: UUID da Conta Conexus
    """
    query = select(UserBlocklist).where(UserBlocklist.user_id == user_id)
    
    if action:
        query = query.where(UserBlocklist.action == action.value)
    
    query = query.order_by(UserBlocklist.created_at.desc()).limit(limit).offset(offset)
    
    result = await db.execute(query)
    items = result.scalars().all()
    
    total_query = select(func.count(UserBlocklist.id)).where(UserBlocklist.user_id == user_id)
    total_result = await db.execute(total_query)
    total = total_result.scalar() or 0
    
    return BlocklistResponse(
        items=[
            BlocklistItem(
                id=item.id,
                phone_hash=item.phone_hmac,
                action=BlockAction(item.action),
                source=item.source,
                created_at=item.created_at.isoformat()
            )
            for item in items
        ],
        total=total
    )


@router.post("/blocks", response_model=BlocklistItem)
async def create_block(
    request: BlocklistCreate,
    user_id: str = Header(..., alias="X-User-ID"),
    db: AsyncSession = Depends(get_db)
):
    """
    Adicionar bloqueio/permissão para o usuário.
    
    Header obrigatório:
    - **X-User-ID**: UUID da Conta Conexus
    """
    # Verificar phone_hash existe
    phone_hash = await db.execute(
        select(PhoneHash).where(PhoneHash.phone_hmac == request.phone_hash.lower())
    )
    if not phone_hash.scalar_one_or_none():
        # Criar se não existe
        phone = PhoneHash(phone_hmac=request.phone_hash.lower(), country="BR")
        db.add(phone)
        await db.flush()

    # Upsert
    existing = await db.execute(
        select(UserBlocklist).where(
            UserBlocklist.user_id == user_id,
            UserBlocklist.phone_hmac == request.phone_hash.lower(),
            UserBlocklist.action == request.action.value
        )
    )
    existing = existing.scalar_one_or_none()

    if existing:
        existing.source = request.source
        existing.created_at = datetime.utcnow()
        item = existing
    else:
        item = UserBlocklist(
            user_id=user_id,
            phone_hmac=request.phone_hash.lower(),
            action=request.action.value,
            source=request.source
        )
        db.add(item)

    await db.commit()
    await db.refresh(item)

    logger.info("block_created", user_id=user_id, phone_hash=request.phone_hash, action=request.action.value)

    return BlocklistItem(
        id=item.id,
        phone_hash=item.phone_hmac,
        action=BlockAction(item.action),
        source=item.source,
        created_at=item.created_at.isoformat()
    )


@router.delete("/blocks/{block_id}")
async def delete_block(
    block_id: int,
    user_id: str = Header(..., alias="X-User-ID"),
    db: AsyncSession = Depends(get_db)
):
    """
    Remover bloqueio/permissão do usuário.
    
    Header obrigatório:
    - **X-User-ID**: UUID da Conta Conexus
    """
    result = await db.execute(
        delete(UserBlocklist).where(
            UserBlocklist.id == block_id,
            UserBlocklist.user_id == user_id
        )
    )
    
    if result.rowcount == 0:
        raise HTTPException(404, "Block not found")
    
    await db.commit()
    
    logger.info("block_deleted", user_id=user_id, block_id=block_id)
    
    return {"success": True, "message": "Block removed"}