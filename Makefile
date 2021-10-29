.PHONY: all run-clj run-bin run-jar debug-figwheel
all: psiclj

psiclj: src/psiclj.clj
	# sudo archlinux-java set java-16-graalvm
	# export PATH="/usr/lib/jvm/java-16-graalvm/bin:$PATH"
	clj -A:native-image

psiclj.jar: src/psiclj.clj
	clj -A:uberjar

.loaddburl:
	@echo file like postgres://user:pass@host:port/dbname

run-clj: .loaddburl 
	PSQLSSLQUERY=sslmode=disable PORT=3002 DATABASE_URL=`cat .localdburl` clojure -m psiclj
run-bin:  psiclj
	PSQLSSLQUERY=sslmode=disable PORT=3003 psiclj
run-jar: psiclj.jar
	PSQLSSLQUERY=sslmode=disable PORT=3004 java -cp psiclj.jar clojure.main -m psiclj

psiclj-heroku: Dockerfile.heroku src/*
	docker build -t psiclj-build . -f Dockerfile.heroku
	docker cp `docker create --rm psiclj-build`:/psiclj/psiclj $@

