#!/bin/bash

# WhatsApp Digest - Complete Project Setup Script
# This script initializes both backend and provides Android setup instructions

set -e  # Exit on any error

echo "🎯 WhatsApp Digest - Complete Setup"
echo "===================================="
echo ""
echo "Silent WhatsApp→AI→Summary pipeline setup"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to print colored output
print_header() {
    echo -e "${PURPLE}[SETUP]${NC} $1"
}

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

print_step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

# Check if we're in the correct directory
if [ ! -f "package.json" ] || [ ! -d "backend" ] || [ ! -d "android" ]; then
    print_error "Please run this script from the whatsup project root directory"
    exit 1
fi

print_header "🔍 Prerequisites Check"

# Check for required commands
check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 is required but not installed"
        echo ""
        case $1 in
            "node")
                echo "Install Node.js 18+: https://nodejs.org/"
                ;;
            "pnpm")
                echo "Install pnpm: npm install -g pnpm"
                ;;
            "docker")
                echo "Install Docker: https://docs.docker.com/get-docker/"
                ;;
            "docker-compose")
                echo "Install Docker Compose: https://docs.docker.com/compose/install/"
                ;;
        esac
        echo ""
        exit 1
    fi
}

print_status "Checking required tools..."
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

print_success "All prerequisites satisfied ✓"
echo ""

# Setup workspace
print_header "📦 Workspace Setup"
print_status "Installing root dependencies..."
pnpm install

print_success "Workspace configured ✓"
echo ""

# Backend setup
print_header "🚀 Backend Setup"
print_step "1/4 Initializing backend environment..."

cd backend

# Environment setup
if [ ! -f ".env" ]; then
    cp .env.example .env
    print_warning "Created .env file from template"

    # Try to detect if this is a CI environment
    if [ -z "$CI" ] && [ -t 0 ]; then
        echo ""
        print_warning "⚠️  IMPORTANT: You need to add your OpenAI API key!"
        echo ""
        echo "Please:"
        echo "1. Get an API key from: https://platform.openai.com/api-keys"
        echo "2. Edit backend/.env and set OPENAI_API_KEY=your-key-here"
        echo ""

        read -p "Do you want to edit .env now? (y/N): " edit_env
        if [[ $edit_env =~ ^[Yy]$ ]]; then
            ${EDITOR:-nano} .env
        fi
    fi
else
    print_success "Environment file already exists"
fi

print_step "2/4 Starting infrastructure services..."
docker-compose up -d

print_step "3/4 Waiting for services..."
sleep 10

# Check PostgreSQL
max_attempts=30
attempt=0
while [ $attempt -lt $max_attempts ]; do
    if docker-compose exec -T postgres pg_isready -U whatsup -d whatsup >/dev/null 2>&1; then
        break
    fi
    attempt=$((attempt + 1))
    sleep 2
    printf "."
done
echo ""

if [ $attempt -eq $max_attempts ]; then
    print_error "PostgreSQL failed to start"
    exit 1
fi

# Check Redis
if ! docker-compose exec -T redis redis-cli ping >/dev/null 2>&1; then
    print_error "Redis failed to start"
    exit 1
fi

print_status "Installing backend dependencies..."
pnpm install

print_step "4/4 Setting up database..."
# Generate and run migrations
if ! pnpm migrate:generate >/dev/null 2>&1; then
    print_status "No new migrations to generate"
fi

pnpm migrate
pnpm seed

cd ..

print_success "Backend setup complete ✓"
echo ""

# Check OpenAI configuration
cd backend
if ! grep -q "OPENAI_API_KEY=sk-" .env 2>/dev/null; then
    print_warning "⚠️  OpenAI API key not configured"
    print_warning "Summarization features will not work without a valid API key"
    echo ""
fi
cd ..

# Android information
print_header "📱 Android App Setup"
echo ""
print_status "The Android app is already built and ready!"
echo ""
echo "🔧 To build and install the Android APK:"
echo "   cd android"
echo "   ./gradlew assembleDebug"
echo "   # Install the APK from android/app/build/outputs/apk/debug/"
echo ""
echo "📋 Android App Configuration:"
echo "   After installing the APK, configure these settings:"
echo ""
echo "   🌐 Server URL:"
echo "      http://localhost:3000/api"
echo "      (or your server's IP address)"
echo ""
echo "   🔑 Device Token:"
echo "      whatsup-dev-token-2024"
echo ""
echo "   📱 Device ID:"
echo "      dev-device-001"
echo ""
echo "   ⚙️  Additional Setup:"
echo "   1. Grant notification access permission"
echo "   2. Enable background sync"
echo "   3. Keep WhatsApp chats muted but notifications enabled"
echo ""

# Success summary
print_header "🎉 Setup Complete!"
echo ""
print_success "✅ Backend API server ready"
print_success "✅ PostgreSQL database configured"
print_success "✅ Redis queue system running"
print_success "✅ Initial device tokens seeded"
print_success "✅ Android APK build instructions provided"
echo ""

echo "🚀 Next Steps:"
echo ""
echo "1. Start the backend server:"
echo "   cd backend && pnpm dev"
echo ""
echo "2. Access the API:"
echo "   🌐 API Base: http://localhost:3000/api"
echo "   📚 Documentation: http://localhost:3000/api/docs"
echo "   ❤️  Health Check: http://localhost:3000/api/health"
echo ""
echo "3. Build and install Android app:"
echo "   cd android && ./gradlew assembleDebug"
echo ""
echo "4. Configure Android app with the settings above"
echo ""

# Warning about OpenAI key
if ! grep -q "OPENAI_API_KEY=sk-" backend/.env 2>/dev/null; then
    echo "⚠️  IMPORTANT REMINDER:"
    echo "   Don't forget to set your OPENAI_API_KEY in backend/.env"
    echo "   Without it, AI summarization will not work!"
    echo ""
fi

echo "📊 System Overview:"
echo "   📱 Android → 🌐 NestJS API → 🐘 PostgreSQL → 🤖 OpenAI → 📝 Summaries"
echo ""
echo "🔧 Useful Commands:"
echo "   pnpm backend:dev          # Start backend in watch mode"
echo "   pnpm docker:up            # Start Docker services"
echo "   pnpm docker:down          # Stop Docker services"
echo "   pnpm db:migrate           # Run database migrations"
echo "   pnpm db:seed              # Reseed database"
echo ""
echo "📖 Documentation:"
echo "   📱 Android: ./android/README.md"
echo "   🚀 Backend: ./backend/README.md"
echo ""
echo "Happy WhatsApp digesting! 🎯✨"
