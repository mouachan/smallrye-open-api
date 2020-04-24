package test.io.smallrye.openapi.runtime.scanner.resources;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import test.io.smallrye.openapi.runtime.scanner.entities.Greeting;

/**
 * Spring.
 * Some basic test, comparing with what we get in the JAX-RS version.
 * See the GreetingPostController in the JAX-RS test
 * 
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
@RestController
@RequestMapping(value = "/greeting", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
public class GreetingPostController {

    // 1) Basic path var test
    @PostMapping("/greet")
    public Greeting greet(@RequestBody Greeting greeting) {
        return greeting;
    }

    // 2) Basic path var test
    //@GetMapping("/hellosPathVariable/{name}")
    //public List<Greeting> hellosPathVariable(@PathVariable(name = "name") String name) {
    //    return Arrays.asList(new Greeting("Hello " + name));
    //}

    // 3) Basic path var with Optional test
    //@GetMapping("/helloOptional/{name}")
    //public Optional<Greeting> helloOptional(@PathVariable(name = "name") String name) {
    //    return Optional.of(new Greeting("Hello " + name));
    //}

    // 4) Basic request param test
    //@GetMapping("/helloRequestParam")
    //public Greeting helloRequestParam(@RequestParam(value = "name", required = false) String name) {
    //    return new Greeting("Hello " + name);
    //}

    // 5) ResponseEntity without a type specified
    //@GetMapping("/helloPathVariableWithResponse/{name}")
    //@APIResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(ref = "#/components/schemas/Greeting")))
    //public ResponseEntity helloPathVariableWithResponse(@PathVariable(name = "name") String name) {
    //    return ResponseEntity.ok(new Greeting("Hello " + name));
    //}

    // 6) ResponseEntity with a type specified (No JaxRS comparison)
    //@GetMapping("/helloPathVariableWithResponseTyped/{name}")
    //public ResponseEntity<Greeting> helloPathVariableWithResponseTyped(@PathVariable(name = "name") String name) {
    //    return ResponseEntity.ok(new Greeting("Hello " + name));
    //}
}
