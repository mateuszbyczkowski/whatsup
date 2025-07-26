# WhatsApp Digest Backend

A **Silent WhatsApp-to-LLM Digest** backend service that receives WhatsApp notifications from Android devices and generates AI-powered summaries using OpenAI's GPT-4o-mini.

## 🚀 Quick Start

### Prerequisites

- Node.js 18+ 
- pnpm 8+
- Docker & Docker Compose
- OpenAI API key

### 1. Environment Setup

```bash
# Clone and navigate to backend
cd whatsup/backend

# Copy environment template
cp .env.example .env

# Edit .env with your values (especially OPENAI_API_KEY)
vim .env
```

### 2. Start Infrastructure

```bash
# Start PostgreSQL and Redis
docker-compose up -d

# Install dependencies
pnpm install

# Generate and run migrations
pnpm migrate:generate
pnpm migrate

# Seed initial device tokens
pnpm seed
```

### 3. Start Development Server

```bash
# Start in watch mode
pnpm dev

# Server runs on http://localhost:3000
# API docs: http://localhost:3000/api/docs
# Health: http://localhost:3000/api/health
```

## 📱 Android App Configuration

After seeding, configure your Android app with:

- **Server URL**: `http://localhost:3000/api`
- **Device Token**: `whatsup-dev-token-2024`
- **Device ID**: `dev-device-001`

## 🏗️ Architecture

```
Android App → POST /api/messages/ingest → PostgreSQL → BullMQ → OpenAI → Summaries
```

### Core Components

1. **Messages Module**: Ingests WhatsApp notifications with deduplication
2. **Summarization Worker**: BullMQ processor with OpenAI integration
3. **Topic Filter**: BERT-based spam/topic filtering
4. **Summaries API**: Retrieves formatted summaries and digests
5. **Device Auth**: Token-based authentication for Android devices

## 🔗 API Endpoints

### Authentication
All endpoints require `Authorization: Bearer <device-token>` header.

### Core Endpoints

```
POST /api/messages/ingest     # Receive message batches from Android
GET  /api/summaries/chats     # List available chats
GET  /api/summaries/:chatId   # Get summaries for specific chat
GET  /api/summaries/stats     # Get summary statistics
GET  /api/health              # Health check
```

### Example Usage

```bash
# Ingest messages (Android app does this automatically)
curl -X POST http://localhost:3000/api/messages/ingest \
  -H "Authorization: Bearer whatsup-dev-token-2024" \
  -H "Content-Type: application/json" \
  -d '{
    "device_id": "dev-device-001",
    "events": [
      {
        "chatId": "group_family_chat",
        "sender": "John Doe",
        "body": "Hey everyone!",
        "timestamp": 1640995200000,
        "packageName": "com.whatsapp"
      }
    ],
    "timestamp": 1640995260000,
    "batch_size": 1
  }'

# Get summaries as JSON
curl -H "Authorization: Bearer whatsup-dev-token-2024" \
  http://localhost:3000/api/summaries/group_family_chat

# Get summaries as Markdown digest
curl -H "Authorization: Bearer whatsup-dev-token-2024" \
  "http://localhost:3000/api/summaries/group_family_chat?format=markdown"
```

## 🗄️ Database Schema

### Tables

- **devices**: Registered Android devices with hashed tokens
- **messages**: WhatsApp messages with deduplication constraints
- **summaries**: AI-generated summaries with metadata

### Key Features

- **Deduplication**: Unique constraint on `(deviceId, chatId, tsOriginal, body)`
- **Encryption**: Device tokens hashed with salt
- **Indexing**: Optimized for time-based queries
- **Retention**: Messages deleted after successful upload (Android side)

## 🔧 Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key (required) | - |
| `DB_HOST` | PostgreSQL host | localhost |
| `REDIS_HOST` | Redis host | localhost |
| `DEVICE_TOKEN_SALT` | Salt for token hashing | (change in prod) |
| `MIN_MESSAGES_FOR_SUMMARY` | Min messages to trigger summary | 5 |
| `BLOCKED_TOPICS` | Comma-separated blocked topics | spam,ads |

### BERT Model Configuration

```env
BERT_MODEL_PATH=Xenova/all-MiniLM-L6-v2
BERT_CACHE_DIR=./models_cache
```

The BERT model downloads automatically on first use (~150MB).

## 🚀 Production Deployment

### Docker Build

```bash
# Build production image
docker build -t whatsup-backend .

# Run with environment
docker run -d \
  --name whatsup-backend \
  -p 3000:3000 \
  -e OPENAI_API_KEY=your-key \
  -e DB_HOST=your-postgres \
  -e REDIS_HOST=your-redis \
  whatsup-backend
```

### Environment Setup

1. **Database**: PostgreSQL 15+ with proper connection pooling
2. **Redis**: Redis 7+ for BullMQ job queues
3. **OpenAI**: Valid API key with sufficient credits
4. **Security**: Change `DEVICE_TOKEN_SALT` and use HTTPS

### Health Checks

- **Liveness**: `GET /api/health/live`
- **Readiness**: `GET /api/health/ready`
- **Full Health**: `GET /api/health`

Monitors: Database, Redis, OpenAI, BERT model, memory, disk

## 🔄 Job Processing

### Summarization Pipeline

1. **Message Ingestion**: Android → Database
2. **Job Queuing**: BullMQ job per chat/hour window
3. **Topic Filtering**: BERT + keyword filtering
4. **AI Summary**: OpenAI GPT-4o-mini
5. **Storage**: PostgreSQL summaries table

### Job Configuration

- **Retry**: 3 attempts with exponential backoff
- **Delay**: 5 minutes after hour boundary for batching
- **Cleanup**: Keep 10 completed, 5 failed jobs

## 📊 Monitoring

### Logs

Structured JSON logs with Pino:

```bash
# View logs in development
pnpm dev

# Production log levels: error, warn, info, debug
LOG_LEVEL=warn
```

### Metrics

Health endpoint provides:

- Database connection status
- Redis queue status  
- OpenAI API connectivity
- BERT model initialization
- Memory/disk usage

## 🛠️ Development

### Commands

```bash
pnpm dev              # Start with watch mode
pnpm build            # Build for production
pnpm start            # Start built app
pnpm lint             # ESLint check
pnpm type-check       # TypeScript check
pnpm migrate:generate # Generate new migration
pnpm migrate          # Run migrations
pnpm seed             # Seed database
```

### Database Operations

```bash
# Generate migration after schema changes
pnpm migrate:generate

# Apply migrations
pnpm migrate

# Reset and reseed (development only)
docker-compose down -v
docker-compose up -d
pnpm migrate
pnpm seed
```

### Adding New Device Tokens

```bash
# Option 1: Use seed script (adds test tokens)
pnpm seed

# Option 2: Manual database insert
# Hash your token: sha256(token + DEVICE_TOKEN_SALT)
# Insert into devices table
```

## 🔒 Security

### Token Security

- Device tokens hashed with salt before storage
- No plaintext tokens in database
- Tokens should be unique per device

### Data Privacy

- Messages processed and deleted immediately after upload
- Only summaries retained long-term
- No personal data in logs
- BERT processing happens locally

### Production Checklist

- [ ] Change `DEVICE_TOKEN_SALT` to random value
- [ ] Use HTTPS for all communications
- [ ] Secure PostgreSQL with strong password
- [ ] Redis password protection
- [ ] Network security (VPC, firewall rules)
- [ ] Log monitoring and alerting
- [ ] Regular security updates

## 📝 API Documentation

Full interactive API docs available at `/api/docs` in development mode.

### Response Formats

**JSON** (default):
```json
{
  "summaries": [...],
  "chatId": "group_family_chat",
  "total": 45,
  "timestamp": 1640995260000
}
```

**Markdown** (`?format=markdown`):
```markdown
# WhatsApp Digest for Family Chat

## January 15, 2024

### 10:00 - 11:00 (25 messages)

The family discussed weekend plans...
```

## 🆘 Troubleshooting

### Common Issues

**OpenAI API Errors**:
```bash
# Check API key validity
curl -H "Authorization: Bearer $OPENAI_API_KEY" \
  https://api.openai.com/v1/models
```

**Database Connection Issues**:
```bash
# Check PostgreSQL
docker-compose logs postgres

# Test connection
psql -h localhost -U whatsup -d whatsup
```

**BERT Model Issues**:
```bash
# Clear model cache and restart
rm -rf ./models_cache
pnpm dev
```

**Queue Processing Issues**:
```bash
# Check Redis
docker-compose logs redis

# Monitor queue
# Visit BullMQ dashboard if installed
```

### Debug Mode

```bash
LOG_LEVEL=debug pnpm dev
```

### Reset Everything

```bash
# Nuclear option: reset all data
docker-compose down -v
rm -rf models_cache
docker-compose up -d
pnpm migrate
pnpm seed
```

## 🤝 Contributing

1. Follow TypeScript strict mode
2. Use Prettier for formatting
3. Add proper error handling
4. Update API documentation
5. Test with real Android app

## 📄 License

Private project for personal use.