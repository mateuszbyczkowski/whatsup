import { drizzle } from 'drizzle-orm/node-postgres';
import { Pool } from 'pg';
import { environment } from '@/environments/environment';
import * as schema from './schema';

// Create PostgreSQL connection pool
const pool = new Pool({
  host: environment.database.host,
  port: environment.database.port,
  user: environment.database.username,
  password: environment.database.password,
  database: environment.database.database,
  ssl: environment.database.ssl ? { rejectUnauthorized: false } : false,
  max: 20,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000,
});

// Create Drizzle instance with schema
export const db = drizzle(pool, { schema });

// Export pool for health checks and manual queries
export { pool };

// Health check function
export async function checkDatabaseHealth(): Promise<boolean> {
  try {
    const client = await pool.connect();
    await client.query('SELECT 1');
    client.release();
    return true;
  } catch (error) {
    console.error('Database health check failed:', error);
    return false;
  }
}

// Graceful shutdown
export async function closeDatabaseConnection(): Promise<void> {
  await pool.end();
}
