FROM python:3.13-alpine

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    HOST=0.0.0.0 \
    PORT=8081

RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --chown=app:app app.py ./
USER app
EXPOSE 8081
CMD ["python", "app.py"]
