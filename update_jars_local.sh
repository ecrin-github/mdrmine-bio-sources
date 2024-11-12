./gradlew install
rm -rf ~/code/mdrmine/sources_jars/*
cp -r ~/.m2/repository/org/intermine/bio* ~/code/mdrmine/sources_jars/
