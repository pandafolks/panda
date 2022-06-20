MONGO_COMPOSE=docker-compose -f ./exampleExternalDependencies/mongo/docker-compose.yml
AUXILIARY_SERVICES_COMPOSE=docker-compose -f ./auxiliaryServices/docker-compose.yml

help:
	@echo "Please use \`make <target>' where <target> is one of"
	@echo "  mongo-run      	to run mongo instance"
	@echo "  mongo-stop  		to stop mongo instance"
	@echo "  mongo-reset    	to clear and rerun mongo database"
	@echo "  mongo-shell  		to open mongo shell"
	@echo "  aux-services-init  	to create example auxiliary services"
	@echo "  aux-services-run   	to run auxiliary services"
	@echo "  aux-services-stop 	to stop auxiliary services"

# MongoDB
mongo-run:
	$(MONGO_COMPOSE) up

mongo-stop:
	$(MONGO_COMPOSE) down

# Stop mongo container -> remove volumes -> recreate and run the container
mongo-reset:
	$(MONGO_COMPOSE) rm -fsv mongo_container
	$(MONGO_COMPOSE) up --force-recreate mongo_container

mongo-shell:
	docker exec -it mongo_container mongosh

# Auxiliary services
aux-services-init:
	bash ./auxiliaryServices/registerInsidePanda.sh

aux-services-run:
	$(AUXILIARY_SERVICES_COMPOSE) up

aux-services-stop:
	$(AUXILIARY_SERVICES_COMPOSE) down
