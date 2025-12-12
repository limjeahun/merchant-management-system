dependencies {
    implementation(project(":common"))
    // DB 관련 (JPA, MySQL)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")
}