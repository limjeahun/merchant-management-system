dependencies {
    implementation(project(":application"))
    implementation(project(":infrastructure")) // Runtime 의존성 (Bean 주입)
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
}