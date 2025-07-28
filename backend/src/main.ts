import { NestFactory } from '@nestjs/core';
import { ValidationPipe, Logger } from '@nestjs/common';
import { SwaggerModule, DocumentBuilder } from '@nestjs/swagger';
import { Logger as PinoLogger } from 'nestjs-pino';
import { AppModule } from './app.module';
let environment;
if (process.env.NODE_ENV === 'production') {
  environment = require('./environments/environment.prod').environment;
} else {
  environment = require('./environments/environment').environment;
}

async function bootstrap() {
  const logger = new Logger('Bootstrap');

  try {
    // Create NestJS application
    const app = await NestFactory.create(AppModule, {
      bufferLogs: true,
    });

    // Use Pino logger
    app.useLogger(app.get(PinoLogger));

    // Global validation pipe with transform
    app.useGlobalPipes(
      new ValidationPipe({
        transform: true,
        whitelist: true,
        forbidNonWhitelisted: true,
        transformOptions: {
          enableImplicitConversion: true,
        },
      })
    );

    // Enable CORS for development
    if (!environment.production) {
      app.enableCors({
        origin: true,
        credentials: true,
      });
    }

    // Set global prefix
    app.setGlobalPrefix('api');

    // Swagger documentation
    if (!environment.production) {
      const config = new DocumentBuilder()
        .setTitle('WhatsApp Digest API')
        .setDescription('Silent WhatsApp notification collector and AI summarizer backend')
        .setVersion('1.0.0')
        .addBearerAuth({
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'Token',
          description: 'Enter your device token',
        })
        .addTag('Messages', 'WhatsApp message ingestion endpoints')
        .addTag('Summaries', 'AI-generated summary retrieval endpoints')
        .addTag('Health', 'System health and monitoring endpoints')
        .build();

      const document = SwaggerModule.createDocument(app, config);
      SwaggerModule.setup('api/docs', app, document, {
        swaggerOptions: {
          persistAuthorization: true,
        },
      });

      logger.log(
        `Swagger documentation available at http://localhost:${environment.port}/api/docs`
      );
    }

    // Start the server
    await app.listen(environment.port);

    logger.log(`🚀 WhatsApp Digest Backend started on port ${environment.port}`);
    logger.log(`📝 Environment: ${environment.production ? 'production' : 'development'}`);
    logger.log(`🔗 Health check: http://localhost:${environment.port}/api/health`);

    if (!environment.production) {
      logger.log(`📚 API docs: http://localhost:${environment.port}/api/docs`);
    }

    // Graceful shutdown handling
    process.on('SIGINT', async () => {
      logger.log('Received SIGINT, shutting down gracefully...');
      await app.close();
      process.exit(0);
    });

    process.on('SIGTERM', async () => {
      logger.log('Received SIGTERM, shutting down gracefully...');
      await app.close();
      process.exit(0);
    });
  } catch (error) {
    logger.error('Failed to start application:', error);
    process.exit(1);
  }
}

bootstrap();
