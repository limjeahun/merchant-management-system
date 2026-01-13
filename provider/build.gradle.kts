dependencies {
    implementation(project(":application")) // Port 구현을 위해
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web") // RestClient
    implementation("org.springframework.boot:spring-boot-starter-webflux") // WebClient for async

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // ONNX Runtime - PaddleOCR 모델 추론용
    implementation("com.microsoft.onnxruntime:onnxruntime:1.17.0")

    // LangChain4j - Gemma3 (Ollama) 연동용 (백업)
    implementation("dev.langchain4j:langchain4j:0.36.2")
    implementation("dev.langchain4j:langchain4j-ollama:0.36.2")

    // Spring AI - Ollama 연동 (Primary)
    implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")
}