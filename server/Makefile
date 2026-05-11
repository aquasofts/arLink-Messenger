.PHONY: build run test docker

APP=nearlink-server

build:
	go build -trimpath -ldflags="-s -w" -o bin/$(APP) ./cmd/nearlink-server

run:
	go run ./cmd/nearlink-server -c config.example.yaml

test:
	go test ./...

docker:
	docker build -t nearlink/$(APP):dev .
