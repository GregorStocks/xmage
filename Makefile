-include .env

# The target directory is used for setting where the output zip files will end up
# You can override this with an environment variable, ex
# TARGET_DIR=my_custom_directory make deploy
# Alternatively, you can set this variable in the .env file
TARGET_DIR ?= deploy/
AI_HARNESS_SERVER ?= localhost
AI_HARNESS_PORT ?= 17171
AI_HARNESS_USER ?= ai-harness
AI_HARNESS_PASSWORD ?=
AI_HARNESS_SERVER_WAIT ?= 90
AI_HARNESS_LOG_DIR ?= .context/ai-harness-logs
AI_HARNESS_JVM_OPENS ?= --add-opens=java.base/java.io=ALL-UNNAMED
AI_HARNESS_SERVER_JVM_ARGS ?= -Dxmage.aiHarnessMode=true -Dxmage.testMode=true
AI_HARNESS_CLIENT_JVM_ARGS ?= -Dxmage.aiHarness.autoConnect=true -Dxmage.aiHarness.autoStart=true -Dxmage.aiHarness.disableWhatsNew=true -Dxmage.aiHarness.server=$(AI_HARNESS_SERVER) -Dxmage.aiHarness.port=$(AI_HARNESS_PORT) -Dxmage.aiHarness.user=$(AI_HARNESS_USER) -Dxmage.aiHarness.password=$(AI_HARNESS_PASSWORD)
AI_HARNESS_ENV_VARS ?= XMAGE_AI_HARNESS=1 XMAGE_AI_HARNESS_USER=$(AI_HARNESS_USER) XMAGE_AI_HARNESS_PASSWORD=$(AI_HARNESS_PASSWORD) XMAGE_AI_HARNESS_DISABLE_WHATS_NEW=1
AI_HARNESS_CONFIG ?= .context/ai-harness-config.json
AI_HARNESS_SKELETON_DELAY ?= 5

.PHONY: clean
clean:
	mvn clean

.PHONY: log-clean
log-clean:
	rm -rf $(AI_HARNESS_LOG_DIR)/*.log $(AI_HARNESS_LOG_DIR)/config $(AI_HARNESS_LOG_DIR)/last.txt
	@echo "Cleaned ai-harness logs"

.PHONY: build
build:
	mvn install package -DskipTests

.PHONY: package
package:
	# Packaging Mage.Client to zip
	cd Mage.Client && mvn package assembly:single
	# Packaging Mage.Server to zip
	cd Mage.Server && mvn package assembly:single
	# Copying the files to the target directory
	mkdir -p $(TARGET_DIR)
	cp ./Mage.Server/target/mage-server.zip $(TARGET_DIR)
	cp ./Mage.Client/target/mage-client.zip $(TARGET_DIR)

# Note that the proper install script is located under ./Utils/build-and-package.pl
# and that should be used instead. This script is purely for convenience.
# The perl script bundles the artifacts into a single zip
.PHONY: install
install: clean build package

.PHONY: ai-harness
ai-harness:
	mvn -q -DskipTests -pl Mage.Server,Mage.Client,Mage.Client.Headless -am compile install
	@mkdir -p $(AI_HARNESS_LOG_DIR)
	@log_dir="$$(pwd)/$(AI_HARNESS_LOG_DIR)"; \
	ts=$$(date +%Y%m%d_%H%M%S); \
	server="$(AI_HARNESS_SERVER)"; \
	port="$(AI_HARNESS_PORT)"; \
	pids=""; \
	kill_tree() { \
		local pid=$$1; \
		local children=$$(pgrep -P $$pid 2>/dev/null); \
		for child in $$children; do \
			kill_tree $$child; \
		done; \
		kill -TERM $$pid 2>/dev/null || true; \
	}; \
	cleanup() { \
		echo ""; \
		echo "Stopping all processes..."; \
		for p in $$pids; do \
			if [ -n "$$p" ] && kill -0 $$p 2>/dev/null; then \
				echo "Killing process tree rooted at PID $$p"; \
				kill_tree $$p; \
			fi; \
		done; \
		sleep 1; \
		for p in $$pids; do \
			if [ -n "$$p" ] && kill -0 $$p 2>/dev/null; then \
				echo "Force killing PID $$p"; \
				kill -9 $$p 2>/dev/null || true; \
			fi; \
		done; \
		exit 0; \
	}; \
	trap cleanup INT TERM; \
	while nc -z "$$server" "$$port" >/dev/null 2>&1; do \
		port=$$((port + 1)); \
	done; \
	config_dir="$$log_dir/config"; \
	mkdir -p "$$config_dir"; \
	config_path="$$config_dir/server_$${ts}.xml"; \
	python3 -c 'import sys, xml.etree.ElementTree as ET; src="Mage.Server/config/config.xml"; dst=sys.argv[1]; port=int(sys.argv[2]); secondary=port+8; tree=ET.parse(src); root=tree.getroot(); server=root.find("server"); assert server is not None, "server element not found in config"; server.set("port", str(port)); server.set("secondaryBindPort", str(secondary)); tree.write(dst, encoding="UTF-8", xml_declaration=True)' "$$config_path" "$$port" || exit 1; \
	server_log="$$log_dir/server_$${ts}.log"; \
	client_log="$$log_dir/client_$${ts}.log"; \
	echo "server_log=$$server_log" > "$$log_dir/last.txt"; \
	echo "client_log=$$client_log" >> "$$log_dir/last.txt"; \
	echo "server=$$server" >> "$$log_dir/last.txt"; \
	echo "port=$$port" >> "$$log_dir/last.txt"; \
	echo "Server log: $$server_log"; \
	echo "Client log: $$client_log"; \
	server_jvm_args="$(AI_HARNESS_JVM_OPENS) $(AI_HARNESS_SERVER_JVM_ARGS) -Dxmage.skipUserStats=true -Dxmage.config.path=$$config_path"; \
	client_jvm_args="$(AI_HARNESS_JVM_OPENS) $(AI_HARNESS_CLIENT_JVM_ARGS) -Dxmage.aiHarness.server=$$server -Dxmage.aiHarness.port=$$port"; \
	(cd Mage.Server && $(AI_HARNESS_ENV_VARS) XMAGE_AI_HARNESS_SERVER=$$server XMAGE_AI_HARNESS_PORT=$$port MAVEN_OPTS="$$server_jvm_args" mvn -q exec:java) >"$$server_log" 2>&1 & \
	server_pid=$$!; \
	pids="$$server_pid"; \
	ready=0; \
	for i in $$(seq 1 $(AI_HARNESS_SERVER_WAIT)); do \
		if nc -z "$$server" "$$port" >/dev/null 2>&1; then \
			ready=1; \
			break; \
		fi; \
		sleep 1; \
	done; \
	if [ "$$ready" -ne 1 ]; then \
		echo "Server failed to start on $$server:$$port within $(AI_HARNESS_SERVER_WAIT)s. See $$server_log"; \
		cleanup; \
		exit 1; \
	fi; \
	skeleton_count=0; \
	if [ -f "$(AI_HARNESS_CONFIG)" ]; then \
		skeleton_count=$$(python3 -c 'import json,sys; c=json.load(open(sys.argv[1])); print(sum(1 for p in c.get("players",[]) if p.get("type")=="skeleton"))' "$(AI_HARNESS_CONFIG)" 2>/dev/null || echo 0); \
	fi; \
	if [ "$$skeleton_count" -gt 0 ]; then \
		echo "Starting $$skeleton_count skeleton client(s)..."; \
		skeleton_names=$$(python3 -c 'import json,sys; c=json.load(open(sys.argv[1])); print(" ".join(p.get("name","skeleton-"+str(i)) for i,p in enumerate(c.get("players",[])) if p.get("type")=="skeleton"))' "$(AI_HARNESS_CONFIG)"); \
		(cd Mage.Client && $(AI_HARNESS_ENV_VARS) XMAGE_AI_HARNESS_SERVER=$$server XMAGE_AI_HARNESS_PORT=$$port MAVEN_OPTS="$$client_jvm_args" mvn -q exec:java) >"$$client_log" 2>&1 & \
		client_pid=$$!; \
		pids="$$pids $$client_pid"; \
		sleep $(AI_HARNESS_SKELETON_DELAY); \
		idx=0; \
		for name in $$skeleton_names; do \
			skeleton_log="$$log_dir/skeleton_$${idx}_$${ts}.log"; \
			echo "skeleton_$${idx}_log=$$skeleton_log" >> "$$log_dir/last.txt"; \
			echo "Skeleton $$idx log: $$skeleton_log"; \
			skeleton_jvm_args="$(AI_HARNESS_JVM_OPENS) -Dxmage.headless.server=$$server -Dxmage.headless.port=$$port -Dxmage.headless.username=$$name"; \
			(cd Mage.Client.Headless && MAVEN_OPTS="$$skeleton_jvm_args" mvn -q exec:java) >"$$skeleton_log" 2>&1 & \
			pids="$$pids $$!"; \
			idx=$$((idx + 1)); \
		done; \
		wait $$client_pid; \
		cleanup; \
	else \
		(cd Mage.Client && $(AI_HARNESS_ENV_VARS) XMAGE_AI_HARNESS_SERVER=$$server XMAGE_AI_HARNESS_PORT=$$port MAVEN_OPTS="$$client_jvm_args" mvn -q exec:java) >"$$client_log" 2>&1 & \
		client_pid=$$!; \
		pids="$$pids $$client_pid"; \
		wait $$client_pid; \
		cleanup; \
	fi
