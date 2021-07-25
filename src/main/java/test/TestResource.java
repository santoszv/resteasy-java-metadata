package test;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;

import java.util.List;

@Consumes("application/json")
@Produces("application/json")
@Path("test")
public class TestResource {

    @GET
    public Void getEmptyBodyVoidResult() {
        throw new RuntimeException("Not Implemented");
    }

    @POST
    public void postEmptyBodyVoidResult() {
        throw new RuntimeException("Not Implemented");
    }

    @GET
    public List<Object> getEmptyBodyNullableListResult() {
        throw new RuntimeException("Not Implemented");
    }

    @POST
    @NotNull
    public List<Object> postEmptyBodyNotNullListResult1() {
        throw new RuntimeException("Not Implemented");
    }

    @POST
    public @NotNull List<Object> postEmptyBodyNotNullListResult2() {
        throw new RuntimeException("Not Implemented");
    }
}
