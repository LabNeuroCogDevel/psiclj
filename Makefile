.PHONY: all run-clj run-bin run-jar debug-figwheel
all: psiclj

psiclj: src/psiclj.clj
	# sudo archlinux-java set java-11-graalvm
	clj -A:native-image

psiclj.jar: src/psiclj.clj
	clj -A:uberjar

run-clj: .loaddburl 
	PORT=3002 DATABASE_URL=`cat .localdburl` clojure -m psiclj
run-bin:  psiclj
	PORT=3003 psiclj
run-jar: psiclj.jar
	PORT=3004 java -cp psiclj.jar clojure.main -m psiclj
