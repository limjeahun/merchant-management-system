dependencies {
    implementation(project(":application")) // Port 구현을 위해
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web") // RestClient
}