import { createHash } from 'crypto';
import { db, pool } from '../database/connection';
import { devices } from '../database/schema';
import { eq } from 'drizzle-orm';
import { environment } from '../environments/environment';

async function seedDatabase() {
  console.log('🌱 Starting database seeding...');

  try {
    // Generate a default device token for development
    const defaultDeviceId = 'dev-device-001';
    const defaultToken = 'whatsup-dev-token-2024';

    // Hash the token with salt
    const tokenHash = createHash('sha256')
      .update(defaultToken + environment.auth.deviceTokenSalt)
      .digest('hex');

    // Check if device already exists
    const existingDevices = await db
      .select()
      .from(devices)
      .where(eq(devices.id, defaultDeviceId))
      .limit(1);

    if (existingDevices.length > 0) {
      console.log(`✅ Device ${defaultDeviceId} already exists, skipping...`);
    } else {
      // Insert the default device
      await db.insert(devices).values({
        id: defaultDeviceId,
        tokenHash,
        lastSeen: new Date(),
        createdAt: new Date(),
        appVersion: '1.0.0',
        platform: 'android',
      });

      console.log(`✅ Created default device: ${defaultDeviceId}`);
    }

    // Create additional test devices if in development
    if (!environment.production) {
      const testDevices = [
        {
          id: 'test-device-001',
          token: 'test-token-001',
        },
        {
          id: 'test-device-002',
          token: 'test-token-002',
        },
      ];

      for (const testDevice of testDevices) {
        const testTokenHash = createHash('sha256')
          .update(testDevice.token + environment.auth.deviceTokenSalt)
          .digest('hex');

        const existing = await db
          .select()
          .from(devices)
          .where(eq(devices.id, testDevice.id))
          .limit(1);

        if (existing.length === 0) {
          await db.insert(devices).values({
            id: testDevice.id,
            tokenHash: testTokenHash,
            lastSeen: new Date(),
            createdAt: new Date(),
            appVersion: '1.0.0',
            platform: 'android',
          });

          console.log(`✅ Created test device: ${testDevice.id}`);
        }
      }
    }

    console.log('\n📋 Device tokens for development:');
    console.log('='.repeat(50));
    console.log(`Device ID: ${defaultDeviceId}`);
    console.log(`Token: ${defaultToken}`);
    console.log('='.repeat(50));

    if (!environment.production) {
      console.log('\n🧪 Test device tokens:');
      console.log('Device ID: test-device-001 | Token: test-token-001');
      console.log('Device ID: test-device-002 | Token: test-token-002');
    }

    console.log('\n🔧 Usage in Android app:');
    console.log(`1. Set server URL to: http://localhost:${environment.port}/api`);
    console.log(`2. Set device token to: ${defaultToken}`);
    console.log(`3. Set device ID to: ${defaultDeviceId}`);

    console.log('\n✅ Database seeding completed successfully!');

  } catch (error) {
    console.error('❌ Database seeding failed:', error);
    process.exit(1);
  } finally {
    await pool.end();
  }
}

// Run if called directly
if (require.main === module) {
  seedDatabase();
}

export { seedDatabase };
