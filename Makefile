MONGO_COMPOSE=docker-compose -f ./exampleExternalDependencies/mongo/docker-compose.yml
AUXILIARY_SERVICES_COMPOSE=docker-compose -f ./auxiliaryServices/docker-compose.yml

# MongoDB
mongo-run:
	$(MONGO_COMPOSE) up

mongo-stop:
	$(MONGO_COMPOSE) down

# Stop mongo container -> remove volumes -> recreate and run container
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