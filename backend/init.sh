#!/bin/bash

# WhatsApp Digest Backend Initialization Script
# This script sets up the development environment from scratch

set -e  # Exit on any error

echo "🚀 WhatsApp Digest Backend Setup"
echo "================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if we're in the correct directory
if [ ! -f "package.json" ]; then
    print_error "Please run this script from the backend directory"
    exit 1
fi

# Check for required commands
check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 is required but not installed"
        exit 1
    fi
}

print_status "Checking prerequisites..."
check_command "node"
check_command "pnpm"
check_command "docker"
check_command "docker-compose"

# Check Node.js version
NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 18 ]; then
    print_error "Node.js 18+ is required (current: $(node -v))"
    exit 1
fi

print_success "Prerequisites check passed"

# Setup environment file
print_status "Setting up environment configuration..."
if [ ! -f ".env" ]; then
    cp .env.example .env
    print_warning "Created .env file from template"
    print_warning "Please edit .env and add your OPENAI_API_KEY before continuing"

    read -p "Do you want to edit .env now? (y/N): " edit_env
    if [[ $edit_env =~ ^[Yy]$ ]]; then
        ${EDITOR:-nano} .env
    else
        print_warning "Remember to set OPENAI_API_KEY in .env before running the server"
    fi
else
    print_success "Environment file already exists"
fi

# Check if OpenAI key is set
if ! grep -q "OPENAI_API_KEY=sk-" .env 2>/dev/null; then
    print_warning "OpenAI API key not detected in .env"
    print_warning "Summarization features will not work without a valid API key"
fi

# Start Docker services
print_status "Starting Docker services (PostgreSQL & Redis)..."
docker-compose up -d

# Wait for services to be ready
print_status "Waiting for services to be ready..."
sleep 10

# Check if PostgreSQL is ready
max_attempts=30
attempt=0
while [ $attempt -lt $max_attempts ]; do
    if docker-compose exec -T postgres pg_isready -U whatsup -d whatsup >/dev/null 2>&1; then
        break
    fi
    attempt=$((attempt + 1))
    sleep 2
    echo -n "."
done

if [ $attempt -eq $max_attempts ]; then
    print_error "PostgreSQL failed to start"
    exit 1
fi

print_success "PostgreSQL is ready"

# Check if Redis is ready
if docker-compose exec -T redis redis-cli ping >/dev/null 2>&1; then
    print_success "Redis is ready"
else
    print_error "Redis failed to start"
    exit 1
fi

# Install dependencies
print_status "Installing dependencies..."
pnpm install

# Generate and run migrations
print_status "Setting up database schema..."
if ! pnpm migrate:generate >/dev/null 2>&1; then
    print_warning "No new migrations to generate"
fi

pnpm migrate

# Seed database
print_status "Seeding database with initial data..."
pnpm seed

print_success "Backend setup completed!"

echo ""
echo "🎉 Setup Summary"
echo "==============="
echo "✅ Docker services started (PostgreSQL, Redis)"
echo "✅ Dependencies installed"
echo "✅ Database schema created"
echo "✅ Initial data seeded"
echo ""
echo "🚀 Next Steps:"
echo "1. Start development server: pnpm dev"
echo "2. Visit API docs: http://localhost:3000/api/docs"
echo "3. Check health: http://localhost:3000/api/health"
echo ""
echo "📱 Android App Configuration:"
echo "   Server URL: http://localhost:3000/api"
echo "   Device Token: whatsup-dev-token-2024"
echo "   Device ID: dev-device-001"
echo ""

# Check if OpenAI key warning should be shown
if ! grep -q "OPENAI_API_KEY=sk-" .env 2>/dev/null; then
    echo "⚠️  Don't forget to set your OPENAI_API_KEY in .env!"
    echo ""
fi

echo "📚 Useful Commands:"
echo "   pnpm dev              # Start development server"
echo "   pnpm build            # Build for production"
echo "   pnpm migrate:generate # Generate new migration"
echo "   pnpm seed             # Reseed database"
echo "   docker-compose logs   # View Docker logs"
echo ""
echo "Happy coding! 🎯"
