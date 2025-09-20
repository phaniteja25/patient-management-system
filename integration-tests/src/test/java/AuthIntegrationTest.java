import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

public class AuthIntegrationTest {

    //arranging before the test
    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = "http://localhost:4004";
    }


    @Test
    public void shouldReturnOKWithValidToken() {
        //1) Arrange
        // 2) Act
        // 3) Test

        String payload = """
                {
                    "email": "testuser@test.com",
                    "password": "password123"
                }
                
                """;

        Response response = given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract().response();

    }
}
