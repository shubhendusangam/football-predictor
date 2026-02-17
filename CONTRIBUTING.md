# Contributing to Football Match Predictor

Thank you for your interest in contributing! 🎉

## How to Contribute

### Reporting Bugs

1. Check existing [Issues](../../issues) to avoid duplicates
2. Create a new issue with:
   - Clear title and description
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment details (Java version, OS)

### Suggesting Features

1. Open an issue with the `enhancement` label
2. Describe the feature and its use case
3. Include any relevant examples

### Pull Requests

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes
4. Run tests: `mvn test`
5. Commit with clear messages: `git commit -m "Add: feature description"`
6. Push to your fork: `git push origin feature/your-feature`
7. Open a Pull Request

### Code Style

- Follow existing code conventions
- Add Javadoc for public methods
- Include unit tests for new features
- Keep commits atomic and focused

### Development Setup

```bash
# Clone your fork
git clone https://github.com/<your-username>/football-prediction.git
cd football-prediction

# Build
mvn clean install

# Run tests
mvn test

# Run locally
mvn spring-boot:run
```

## Questions?

Feel free to open an issue for any questions!

