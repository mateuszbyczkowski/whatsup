import { registerAs } from '@nestjs/config';
import { environment } from '@/environments/environment';

export default registerAs('app', () => ({
  port: environment.port,
  environment: environment.production ? 'production' : 'development',

  // Database configuration
  database: {
    host: environment.database.host,
    port: environment.database.port,
    username: environment.database.username,
    password: environment.database.password,
    database: environment.database.database,
    ssl: environment.database.ssl,
  },

  // Redis configuration
  redis: {
    host: environment.redis.host,
    port: environment.redis.port,
    password: environment.redis.password,
    db: environment.redis.db,
  },

  // OpenAI configuration
  openai: {
    apiKey: environment.openai.apiKey,
    model: environment.openai.model,
    maxTokens: environment.openai.maxTokens,
  },

  // Security configuration
  auth: {
    deviceTokenSalt: environment.auth.deviceTokenSalt,
  },

  // Logging configuration
  logging: {
    level: environment.logging.level,
    pretty: environment.logging.pretty,
  },

  // BERT configuration
  bert: {
    modelPath: environment.bert.modelPath,
    cacheDir: environment.bert.cacheDir,
  },

  // Summarization configuration
  summarization: {
    batchWindowHours: environment.summarization.batchWindowHours,
    minMessagesForSummary: environment.summarization.minMessagesForSummary,
    blockedTopics: environment.summarization.blockedTopics,
  },
}));
