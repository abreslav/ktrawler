java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../kotlin > kotlin.txt
java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../debug-projects/jetpeople > jetpeople.txt
java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../debug-projects/JetProfile > JetProfile.txt
java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../debug-projects/ktor > ktor.txt
java -jar out/artifacts/ktrawler_jar/ktrawler.jar -stats-only -only-analyze ../idea/src/ultimate > intellij-idea-ultimate.txt