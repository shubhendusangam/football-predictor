# Model Training Service

A separate Spring Boot microservice for training and testing machine learning models for football match prediction.

## Overview

This service is responsible for:
- Training the Random Forest model on historical match data
- Testing and evaluating model performance
- Providing model information via REST API
- Automatically retraining the model twice monthly (1st and 15th at 3:00 AM)
- Storing trained models in the shared data folder

## Architecture

- **Port**: 8081 (configurable)
- **Database**: Shared H2 database with main application (read-only access)
- **Model Storage**: `../data/match_predictor.model` (shared with main app)
- **Framework**: Spring Boot 4.0.2, Java 21
- **ML Library**: Weka 3.8.6

## API Endpoints

### 1. Train Model
```bash
POST http://localhost:8081/api/training/train
```

**Description**: Trains a new Random Forest model on historical match data.

**Response**:
```json
{
  "success": true,
  "message": "Model training completed successfully",
  "report": "... detailed training report ...",
  "trainingTimeMs": 5432
}
```

### 2. Test Model
```bash
POST http://localhost:8081/api/training/test
```

**Description**: Tests the trained model against the test dataset.

**Response**:
```json
{
  "success": true,
  "message": "Model testing completed successfully",
  "report": "... detailed test report ..."
}
```

### 3. Get Model Info
```bash
GET http://localhost:8081/api/training/model-info
```

**Description**: Retrieves information about the current model.

**Response**:
```json
{
  "success": true,
  "modelInfo": {
    "modelExists": true,
    "modelPath": "../data/match_predictor.model",
    "modelSize": 1234567,
    "lastModified": "2026-02-18T03:00:00",
    "totalMatches": 3800
  }
}
```

## Configuration

All configuration is in `application.properties`:

```properties
# Server
server.port=8081

# Database (shared with main app)
spring.datasource.url=jdbc:h2:file:../data/footballdb

# Model Storage
model.output.path=../data/match_predictor.model

# Training Parameters
model.training.min-matches=100
model.training.train-split=0.8

# Cross-validation
model.crossvalidation.enabled=true
model.crossvalidation.folds=10

# Scheduled Training (1st and 15th at 3 AM)
training.schedule.enabled=true
training.schedule.cron=0 0 3 1,15 * *
```

## Scheduled Training

The service automatically trains the model **twice monthly**:
- **Schedule**: 1st and 15th of each month at 3:00 AM
- **Configurable**: Set `training.schedule.enabled=false` to disable
- **Custom Cron**: Change `training.schedule.cron` property

## Building and Running

### Local Development

```bash
# Build
cd model-training-service
mvn clean install

# Run
mvn spring-boot:run
```

### Production Build

```bash
mvn clean package
java -jar target/model-training-service-1.0.0.jar
```

### Docker

```bash
# Build image
docker build -t model-training-service:1.0.0 .

# Run container
docker run -p 8081:8081 \
  -v $(pwd)/data:/app/data \
  model-training-service:1.0.0
```

## Integration with Main Application

The main football prediction application (port 8080) uses the model trained by this service:

1. **Shared Database**: Both services read from the same H2 database
2. **Shared Model File**: Model is stored in `../data/match_predictor.model`
3. **On-Demand Training**: Main app can trigger training via HTTP calls
4. **Automatic Updates**: Scheduled training keeps the model fresh

## Logs

Logs are stored in `../logs/model-training/`:
- `training.log` - All training activities
- `api.log` - API request/response logs
- `error.log` - Error logs only

## Model Training Process

1. **Data Loading**: Fetches all matches from database ordered by date
2. **Feature Engineering**: Builds 25 features for each match
3. **Temporal Split**: 80% training, 20% testing (chronological)
4. **Model Training**: Random Forest with 100 trees
5. **Evaluation**: Tests on held-out test set
6. **Persistence**: Saves model to shared data folder

## Performance

- **Training Time**: ~5-10 seconds for 3800 matches
- **Accuracy**: ~50-55% (baseline ~45%)
- **Memory**: ~512MB recommended
- **Disk**: Model file ~1-2MB

## Troubleshooting

### Model Training Fails

```bash
# Check database connection
curl http://localhost:8081/api/training/model-info

# Check logs
tail -f ../logs/model-training/training.log
```

### Scheduled Training Not Working

```bash
# Verify scheduler is enabled
grep "training.schedule.enabled" src/main/resources/application.properties

# Check scheduler logs
grep "SCHEDULED" ../logs/model-training/training.log
```

## Development

### Adding New Features

1. Update `MatchFeatures.java` with new feature fields
2. Update `FeatureEngineeringService.java` to calculate features
3. Update `ModelTrainingService.buildAttributes()` to add Weka attributes
4. Update `ModelTrainingService.toWekaInstance()` to populate values
5. Update feature indices constants

### Changing ML Algorithm

Currently uses Random Forest. To use a different algorithm:

1. Modify `ModelTrainingService.trainModel()` method
2. Replace `RandomForest` with desired classifier
3. Update hyperparameters
4. Test and evaluate performance

## License

Same as parent project.

