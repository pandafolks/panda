MONGO_COMPOSE=docker-compose -f ./exampleExternalDependencies/mongo/docker-compose.yml
MONGO_CONTAINER_NAME=mongo_container
AUXILIARY_SERVICES_COMPOSE=docker-compose -f ./exampleExternalDependencies/auxiliaryServices/docker-compose.yml

help:
	@echo "Please use \`make <target>' where <target> is one of"
	@echo "  mongo-run      	to run mongo instance"
	@echo "  mongo-stop  		to stop mongo instance"
	@echo "  mongo-reset    	to clear and rerun mongo database"
	@echo "  mongo-shell  		to open mongo shell"
	@echo "  aux-services-run   	to initialize and run auxiliary services"
	@echo "  aux-services-stop 	to stop auxiliary services"

# MongoDB
mongo-run:AUXILIARY_SERVICES_COMPOSE
	$(MONGO_COMPOSE) up

mongo-stop:
	$(MONGO_COMPOSE) down

# Stop mongo container -> remove volumes -> recreate and run the container
mongo-reset:
	$(MONGO_COMPOSE) down -v $(MONGO_CONTAINER_NAME)
	$(MONGO_COMPOSE) up --force-recreate $(MONGO_CONTAINER_NAME)

mongo-shell:
	docker exec -it $(MONGO_CONTAINER_NAME) mongosh

# Auxiliary services
aux-services-run:
	bash ./exampleExternalDependencies/auxiliaryServices/registerInsidePanda.sh && \
	$(AUXILIARY_SERVICES_COMPOSE) up

aux-services-stop:
	$(AUXILIARY_SERVICES_COMPOSE) down
