.PHONY: all run-clj run-bin run-jar test
all: psiclj

psiclj: src/psiclj.clj src/*html src/*
	# sudo archlinux-java set java-16-graalvm
	# export PATH="/usr/lib/jvm/java-16-graalvm/bin:$PATH"
	clj -A:native-image

psiclj.exe: src/psiclj.clj src/*html src/*
	# for windows
	./compile.bat

psiclj.jar: deps.edn src/*
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

test: psiclj
	bats test/system_tests.bats

node_modules/@testing-library/:
	npm install --save-dev @testing-library/dom
test-js: node_modules/@testing-library/
	# npm install -g jest
	jest test/index.test.ts
