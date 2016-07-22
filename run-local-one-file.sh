echo "kotlin" > report.txt
java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../kotlin >> report.txt
echo "JetPeople" >> report.txt
java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../debug-projects/jetpeople >> report.txt
echo "JetProfile" >> report.txt
java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../debug-projects/JetProfile >> report.txt
echo "Ktor" >> report.txt
java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../debug-projects/ktor >> report.txt
echo "IntelliJ IDEA Ultimate" >> report.txt
java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../idea/src/ultimate >> report.txt