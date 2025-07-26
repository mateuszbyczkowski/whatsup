import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { pipeline, Pipeline } from '@xenova/transformers';
import { environment } from '@/environments/environment';

export interface TopicClassificationResult {
  label: string;
  score: number;
}

@Injectable()
export class TopicFilterService implements OnModuleInit {
  private readonly logger = new Logger(TopicFilterService.name);
  private classificationPipeline: Pipeline | null = null;
  private isInitialized = false;

  async onModuleInit() {
    await this.initializeModel();
  }

  private async initializeModel(): Promise<void> {
    try {
      this.logger.log('Initializing BERT model for topic classification...');

      // Initialize the text classification pipeline
      this.classificationPipeline = await pipeline(
        'zero-shot-classification',
        environment.bert.modelPath,
        {
          cache_dir: environment.bert.cacheDir,
          local_files_only: false,
        }
      );

      this.isInitialized = true;
      this.logger.log('BERT model initialized successfully');
    } catch (error) {
      this.logger.error('Failed to initialize BERT model:', error);
      // Don't throw - allow service to start without BERT (fallback to keyword filtering)
      this.isInitialized = false;
    }
  }

  async isBlockedTopic(messageText: string): Promise<boolean> {
    try {
      // Quick keyword-based filtering first (performance optimization)
      if (this.hasBlockedKeywords(messageText)) {
        this.logger.debug('Message blocked by keyword filter');
        return true;
      }

      // If BERT model is available, use semantic classification
      if (this.isInitialized && this.classificationPipeline) {
        return await this.classifyWithBERT(messageText);
      }

      // Fallback to keyword-only filtering if BERT unavailable
      return false;
    } catch (error) {
      this.logger.warn('Topic filtering failed, allowing message:', error);
      return false; // Allow message on error
    }
  }

  private hasBlockedKeywords(text: string): boolean {
    const blockedKeywords = [
      // Spam indicators
      'click here',
      'limited time',
      'act now',
      'free offer',
      'guaranteed',
      'make money fast',
      'work from home',
      'earn $$',
      'get rich quick',

      // Promotional content
      'buy now',
      'discount',
      'sale ends',
      'special offer',
      'promo code',
      'save up to',
      'clearance',
      'liquidation',
      'going out of business',

      // Suspicious links/content
      'bit.ly',
      'tinyurl',
      'click link',
      'forward this message',
      'share with friends',
      'send to contacts',

      // Cryptocurrency/investment spam
      'bitcoin',
      'crypto investment',
      'trading signals',
      'forex',
      'investment opportunity',
      'double your money',

      // Generic spam patterns
      'congratulations you won',
      'you have been selected',
      'claim your prize',
      'verify account',
      'update payment',
    ];

    const lowerText = text.toLowerCase();

    return blockedKeywords.some(keyword => lowerText.includes(keyword.toLowerCase()));
  }

  private async classifyWithBERT(text: string): Promise<boolean> {
    try {
      // Define topic categories for classification
      const candidateLabels = [
        'personal conversation',
        'family discussion',
        'work communication',
        'social planning',
        'news sharing',
        'spam content',
        'promotional material',
        'advertisement',
        'suspicious content',
        'financial scam',
      ];

      // Classify the text
      const result = await this.classificationPipeline(text, candidateLabels);

      if (!result || !result.labels || !result.scores) {
        this.logger.warn('Invalid BERT classification result');
        return false;
      }

      // Get the top classification
      const topLabel = result.labels[0];
      const topScore = result.scores[0];

      this.logger.debug(`BERT classification: ${topLabel} (${topScore.toFixed(3)})`);

      // Block if classified as spam/promotional with high confidence
      const blockedLabels = [
        'spam content',
        'promotional material',
        'advertisement',
        'suspicious content',
        'financial scam',
      ];
      const isBlockedLabel = blockedLabels.includes(topLabel);
      const isHighConfidence = topScore > 0.7;

      return isBlockedLabel && isHighConfidence;
    } catch (error) {
      this.logger.error('BERT classification failed:', error);
      return false; // Allow message on error
    }
  }

  async getTopicClassification(text: string): Promise<TopicClassificationResult[]> {
    if (!this.isInitialized || !this.classificationPipeline) {
      throw new Error('BERT model not initialized');
    }

    try {
      const candidateLabels = [
        'personal conversation',
        'family discussion',
        'work communication',
        'social planning',
        'news sharing',
        'educational content',
        'entertainment',
        'technical discussion',
        'spam content',
        'promotional material',
      ];

      const result = await this.classificationPipeline(text, candidateLabels);

      if (!result || !result.labels || !result.scores) {
        return [];
      }

      return result.labels.map((label: string, index: number) => ({
        label,
        score: result.scores[index],
      }));
    } catch (error) {
      this.logger.error('Topic classification failed:', error);
      return [];
    }
  }

  isModelInitialized(): boolean {
    return this.isInitialized;
  }

  getBlockedTopics(): string[] {
    return environment.summarization.blockedTopics;
  }

  async healthCheck(): Promise<boolean> {
    try {
      if (!this.isInitialized) {
        return false;
      }

      // Test classification with a simple message
      const testResult = await this.isBlockedTopic('Hello, how are you?');
      return typeof testResult === 'boolean';
    } catch (error) {
      this.logger.error('Topic filter health check failed:', error);
      return false;
    }
  }
}
