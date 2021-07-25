package resteasy.metadata.java;

import jakarta.json.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.core.ResourceInvoker;
import org.jboss.resteasy.core.ResourceLocatorInvoker;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.ResourceMethodRegistry;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.metadata.MethodParameter;
import org.jboss.resteasy.spi.metadata.ResourceLocator;
import org.jboss.resteasy.spi.metadata.ResourceMethod;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@WebServlet("/resteasy/JavaMetadata")
public final class JavaMetadata extends HttpServlet {

    private JsonArray metadata;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            JsonArray metadata;
            synchronized (this) {
                JsonArray result = this.metadata;
                if (result == null) {
                    List<ResourceLocator> resourceLocators = getResourceLocators();
                    if (resourceLocators == null) {
                        resp.setContentType("text/plain");
                        resp.getWriter().println("RestEasy Deployment Was Not Found");
                        return;
                    }
                    result = getMetadata(resourceLocators);
                    this.metadata = result;
                }
                metadata = result;
            }
            resp.setContentType("application/json");
            JsonWriter writer = Json.createWriter(resp.getWriter());
            writer.writeArray(metadata);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ServletException(e);
        }
    }

    private List<ResourceLocator> getResourceLocators() throws NoSuchFieldException, IllegalAccessException {
        @SuppressWarnings("unchecked") Map<String, ResteasyDeployment> deployments = (Map<String, ResteasyDeployment>) getServletContext().getAttribute(ResteasyContextParameters.RESTEASY_DEPLOYMENTS);
        if (deployments == null) return null;
        List<ResourceLocator> result = new ArrayList<>();
        for (ResteasyDeployment deployment : deployments.values()) {
            ResourceMethodRegistry registry = (ResourceMethodRegistry) deployment.getRegistry();
            for (List<ResourceInvoker> resourceInvokers : registry.getBounded().values()) {
                for (ResourceInvoker resourceInvoker : resourceInvokers) {
                    if (resourceInvoker instanceof ResourceLocatorInvoker || resourceInvoker instanceof ResourceMethodInvoker) {
                        Field field = resourceInvoker.getClass().getDeclaredField("method");
                        field.setAccessible(true);
                        ResourceLocator resourceLocator = (ResourceLocator) field.get(resourceInvoker);
                        Annotation[] declaredAnnotations = resourceLocator.getResourceClass().getClazz().getDeclaredAnnotations();
                        boolean isKotlinClass = Arrays.stream(declaredAnnotations).anyMatch(ann -> ann.annotationType().getName().equals("kotlin.Metadata"));
                        if (!isKotlinClass) {
                            result.add(resourceLocator);
                        }
                    }
                }
            }
        }
        return result;
    }

    private JsonArray getMetadata(List<ResourceLocator> resourceLocators) {
        JsonArrayBuilder result = Json.createArrayBuilder();
        for (ResourceLocator resourceLocator : resourceLocators) {
            JsonObjectBuilder metadata = Json.createObjectBuilder();
            Class<?> resourceClass = resourceLocator.getResourceClass().getClazz();
            Method method = resourceLocator.getMethod();
            metadata.add("resourceClass", resourceClass.getName());
            metadata.add("returnType", getMetadata(method.getAnnotatedReturnType()));
            metadata.add("method", method.getName());
            JsonArrayBuilder params = Json.createArrayBuilder();
            MethodParameter[] methodParameters = resourceLocator.getParams();
            for (int i = 0, n = methodParameters.length; i < n; i++) {
                JsonObjectBuilder param = Json.createObjectBuilder();
                MethodParameter methodParameter = methodParameters[i];
                AnnotatedType[] annotatedParameterTypes = method.getAnnotatedParameterTypes();
                AnnotatedType annotatedParameterType = annotatedParameterTypes[i];
                param.add("type", getMetadata(annotatedParameterType));
                param.add("paramType", methodParameter.getParamType().name());
                param.add("paramName", methodParameter.getParamName());
                params.add(param.build());
            }
            metadata.add("params", params.build());
            String fullPath = resourceLocator.getFullpath();
            if (fullPath == null) {
                metadata.addNull("fullPath");
            } else {
                metadata.add("fullPath", fullPath);
            }
            String path = resourceLocator.getPath();
            if (path == null) {
                metadata.addNull("path");
            } else {
                metadata.add("path", path);
            }
            JsonArrayBuilder httpMethods = Json.createArrayBuilder();
            JsonArrayBuilder consumes = Json.createArrayBuilder();
            JsonArrayBuilder produces = Json.createArrayBuilder();
            if (resourceLocator instanceof ResourceMethod) {
                metadata.add("resourceMethod", true);
                ResourceMethod resourceMethod = (ResourceMethod) resourceLocator;
                for (String httpMethod : resourceMethod.getHttpMethods()) {
                    httpMethods.add(httpMethod);
                }
                for (MediaType produce : resourceMethod.getProduces()) {
                    produces.add(produce.toString());
                }
                for (MediaType consume : resourceMethod.getConsumes()) {
                    consumes.add(consume.toString());
                }
            } else {
                metadata.add("resourceMethod", false);
            }
            metadata.add("httpMethods", httpMethods.build());
            metadata.add("produces", produces.build());
            metadata.add("consumes", consumes.build());
            result.add(metadata.build());
        }
        return result.build();
    }

    private JsonObject getMetadata(AnnotatedType annotatedType) {
        JsonObjectBuilder result = Json.createObjectBuilder();
        boolean isNotNull = annotatedType.getAnnotation(NotNull.class) != null;
        Type type = annotatedType.getType();
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (clazz.isPrimitive()) {
                isNotNull = true;
                clazz = MethodType.methodType(clazz).wrap().returnType();
            }
            result.add("qualifiedName", clazz.getName());
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Class<?> clazz = (Class<?>) parameterizedType.getRawType();
            result.add("qualifiedName", clazz.getName());
        } else {
            result.add("qualifiedName", "???");
        }
        result.add("isMarkedNullable", !isNotNull);
        JsonArrayBuilder typeArguments = Json.createArrayBuilder();
        if (annotatedType instanceof AnnotatedParameterizedType) {
            AnnotatedParameterizedType annotatedParameterizedType = (AnnotatedParameterizedType) annotatedType;
            AnnotatedType[] annotatedActualTypeArguments = annotatedParameterizedType.getAnnotatedActualTypeArguments();
            for (AnnotatedType annotatedActualTypeArgument : annotatedActualTypeArguments) {
                typeArguments.add(getMetadata(annotatedActualTypeArgument));
            }
        }
        result.add("typeArguments", typeArguments.build());
        return result.build();
    }
}
