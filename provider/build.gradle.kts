dependencies {
    implementation(project(":application")) // Port 구현을 위해
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web") // RestClient

    // DJL - PaddleOCR ONNX 추론용
    implementation("ai.djl:api:0.30.0")
    implementation("ai.djl.onnxruntime:onnxruntime-engine:0.30.0")
    implementation("ai.djl.opencv:opencv:0.30.0")

    // LangChain4j - Gemma2 연동용
    implementation("dev.langchain4j:langchain4j:0.36.2")
    implementation("dev.langchain4j:langchain4j-ollama:0.36.2")
}