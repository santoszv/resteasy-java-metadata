plugins {
    war
}

repositories {
    mavenCentral()
}

dependencies {

    // Jakarta EE
    compileOnly("jakarta.platform:jakarta.jakartaee-api:9.0.0")

    // Rest Easy
    compileOnly(files("lib/resteasy-jaxrs-3.15.1.Final-ee9.jar"))
}