import { pgTable, varchar, timestamp, text, integer, boolean, index, uniqueIndex } from 'drizzle-orm/pg-core';
import { relations } from 'drizzle-orm';

// Devices table - stores registered Android devices
export const devices = pgTable('devices', {
  id: varchar('id', { length: 255 }).primaryKey(),
  tokenHash: varchar('token_hash', { length: 255 }).notNull().unique(),
  lastSeen: timestamp('last_seen').defaultNow().notNull(),
  createdAt: timestamp('created_at').defaultNow().notNull(),
  appVersion: varchar('app_version', { length: 50 }),
  platform: varchar('platform', { length: 20 }).default('android'),
}, (table) => ({
  tokenHashIdx: uniqueIndex('devices_token_hash_idx').on(table.tokenHash),
  lastSeenIdx: index('devices_last_seen_idx').on(table.lastSeen),
}));

// Messages table - stores WhatsApp messages from Android notifications
export const messages = pgTable('messages', {
  id: varchar('id', { length: 255 }).primaryKey(),
  deviceId: varchar('device_id', { length: 255 }).notNull(),
  chatId: varchar('chat_id', { length: 255 }).notNull(),
  sender: varchar('sender', { length: 255 }).notNull(),
  body: text('body').notNull(),
  tsOriginal: timestamp('ts_original').notNull(), // Original message timestamp from Android
  packageName: varchar('package_name', { length: 100 }).notNull(),
  createdAt: timestamp('created_at').defaultNow().notNull(),
  processedAt: timestamp('processed_at'),
  isProcessed: boolean('is_processed').default(false).notNull(),
}, (table) => ({
  deviceChatTimestampUnique: uniqueIndex('messages_device_chat_ts_body_unique').on(
    table.deviceId,
    table.chatId,
    table.tsOriginal,
    table.body
  ),
  chatIdIdx: index('messages_chat_id_idx').on(table.chatId),
  deviceIdIdx: index('messages_device_id_idx').on(table.deviceId),
  tsOriginalIdx: index('messages_ts_original_idx').on(table.tsOriginal),
  processedIdx: index('messages_processed_idx').on(table.isProcessed, table.processedAt),
  chatTimeIdx: index('messages_chat_time_idx').on(table.chatId, table.tsOriginal),
}));

// Summaries table - stores AI-generated summaries
export const summaries = pgTable('summaries', {
  id: varchar('id', { length: 255 }).primaryKey(),
  chatId: varchar('chat_id', { length: 255 }).notNull(),
  summaryText: text('summary_text').notNull(),
  periodStart: timestamp('period_start').notNull(),
  periodEnd: timestamp('period_end').notNull(),
  messageCount: integer('message_count').notNull(),
  model: varchar('model', { length: 100 }).notNull(),
  tokensUsed: integer('tokens_used'),
  createdAt: timestamp('created_at').defaultNow().notNull(),
  metadata: text('metadata'), // JSON string for additional data
}, (table) => ({
  chatPeriodIdx: index('summaries_chat_period_idx').on(table.chatId, table.periodStart, table.periodEnd),
  chatIdIdx: index('summaries_chat_id_idx').on(table.chatId),
  periodStartIdx: index('summaries_period_start_idx').on(table.periodStart),
  createdAtIdx: index('summaries_created_at_idx').on(table.createdAt),
}));

// Define relationships
export const devicesRelations = relations(devices, ({ many }) => ({
  messages: many(messages),
}));

export const messagesRelations = relations(messages, ({ one }) => ({
  device: one(devices, {
    fields: [messages.deviceId],
    references: [devices.id],
  }),
}));

export const summariesRelations = relations(summaries, ({ many }) => ({
  // Note: summaries don't have direct FK to messages for performance
  // They're linked by chatId and time period
}));

// Export types for use in services
export type Device = typeof devices.$inferSelect;
export type NewDevice = typeof devices.$inferInsert;

export type Message = typeof messages.$inferSelect;
export type NewMessage = typeof messages.$inferInsert;

export type Summary = typeof summaries.$inferSelect;
export type NewSummary = typeof summaries.$inferInsert;
