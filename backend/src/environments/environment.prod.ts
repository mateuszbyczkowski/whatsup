export const environment = {
  production: true,
  port: parseInt(process.env.PORT || '3000', 10),

  // Database
  database: {
    host: process.env.DB_HOST!,
    port: parseInt(process.env.DB_PORT || '5432', 10),
    username: process.env.DB_USERNAME!,
    password: process.env.DB_PASSWORD!,
    database: process.env.DB_NAME!,
    ssl: process.env.DB_SSL === 'true',
  },

  // Redis
  redis: {
    host: process.env.REDIS_HOST!,
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD,
    db: parseInt(process.env.REDIS_DB || '0', 10),
  },

  // OpenAI
  openai: {
    apiKey: process.env.OPENAI_API_KEY!,
    model: process.env.OPENAI_MODEL || 'gpt-4o-mini',
    maxTokens: parseInt(process.env.OPENAI_MAX_TOKENS || '1000', 10),
  },

  // Security
  auth: {
    deviceTokenSalt: process.env.DEVICE_TOKEN_SALT!,
  },

  // Logging
  logging: {
    level: process.env.LOG_LEVEL || 'warn',
    pretty: false,
  },

  // BERT Model
  bert: {
    modelPath: process.env.BERT_MODEL_PATH || 'Xenova/all-MiniLM-L6-v2',
    cacheDir: process.env.BERT_CACHE_DIR || '/app/models_cache',
  },

  // Summarization
  summarization: {
    batchWindowHours: parseInt(process.env.SUMMARY_BATCH_WINDOW_HOURS || '1', 10),
    minMessagesForSummary: parseInt(process.env.MIN_MESSAGES_FOR_SUMMARY || '5', 10),
    blockedTopics: (process.env.BLOCKED_TOPICS || 'spam,advertisements,promotions').split(','),
  },
};
