import type { Config } from 'drizzle-kit';
import { environment } from './src/environments/environment';

export default {
  schema: './src/database/schema.ts',
  out: './drizzle',
  driver: 'pg',
  dbCredentials: {
    host: environment.database.host,
    port: environment.database.port,
    user: environment.database.username,
    password: environment.database.password,
    database: environment.database.database,
    ssl: environment.database.ssl,
  },
  verbose: true,
  strict: true,
} satisfies Config;
