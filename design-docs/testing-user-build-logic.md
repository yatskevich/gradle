The support Gradle provides for users to test their custom build logic is inadequate. Both the tooling and documentation is deficient. As a philosophy of Gradle is that a build is just another kind of software endeavour, it should be possible to follow the state of the art software development practices such as testing, continuous integration and TDD.

The concept of a Gradle “test-kit” will be developed. This will be one or more jars that contain utilities to aid in testing Gradle code; these jars will not be considered part of the normal Gradle runtime. It will be testing technology agnostic, and not Groovy dependent at its core. There may be adapters added for different testing technologies (e.g. Groovy, Spock, JUnit) added over time. 

The Gradle project internally already has something resembling a “test-kit”. Initially, the public “test-kit” will have no relation to this internal test-kit and will focus on the testing needs of users which may prove to be a subset of the requirements for testing Gradle itself. If there is significant convergence, the internal kit will over time come to be based on the public kit. The design of the public kit will likely be inspired by the internal kit, but need not share code.

# Use cases

Users should be able to fully test their custom build logic. This is not about a generic _build_ testing feature. That is, the focus will be on testing reusable build logic such as plugins & tasks etc. Furthermore, the focus will be on object plugins (as opposed to script plugins).

The tools developed may support testing build instances, but this is not a primary design goal.

# Implementation plan

## Story 1: A user adds the test kit to their compile classpath in Gradle

This will establish the “delivery mechanism” for the toolkit

### User visible changes

The following is an example of a Groovy Gradle project that builds a Gradle plugin and uses the test kit, with JUnit.

    apply plugin: "groovy"
    
    dependencies {
        groovy localGroovy()
        compile gradleApi()
        testCompile gradleTestKit()
        testCompile "junit:junit:4.10"
    }

### Sad day cases

The user may try this with a version of Gradle that doesn't ship the test kit. They will get an MME for method 'gradleTestKit' on DependencyHandler.

### Test coverage

An integration test for the build script above can be run that attempts to compile some code against classes in the test kit.

### Implementation approach

1. A “test-kit” project will be added as a subproject of the build. 
2. The jar (and its dependencies) will be made available
    1. added to the Gradle distribution, but Gradle's bootstrapping will have to be updated to not load this into the Gradle runtime.
    2. The jars are not part of the distribution, but uploaded to repo.gradle.org and/or maven central.
3. A `gradleTestKit()` dependency notation will be added to DependencyHandler

note: It may be worth at this point adding a `DependencyHandler.getGradle()` method that returns a `GradleDependencyNotations` object with methods like `api()`, `groovy()`, `testKit()` (and deprecate `gradleApi()` etc.)

    dependencies {
        groovy gradle.groovy()
        compile gradle.api()
        testCompile gradle.testKit()
    }

## Story 2: A executes a Gradle build programatically via their IDE, using the test kit

Note: what we traditionally call “integration” tests for the Gradle project are really “functional” (or acceptance) tests. The public test kit will describe such tests as “functional” tests.

A set of interfaces/builders will be developed to provide programmatic execution of Gradle builds. This will be a more targeted version of the interface provided by the Tooling API, and also not bound to it.

### User visible changes

    package org.gradle.testkit.functional;

    public interface GradleRunner {
        File getDirectory();
        void setDirectory(File directory);

        List<String> getArguments();
        void setArguments(List<String> string);

        ExecutionResult run();
    }
    
    public interface ExecutionResult {
        String getStandardOutput();
        String getStandardError();
    }
    
    public class GradleRunnerFactory {
        public static GradleRunner create() { /* … */ }
    }
        
    class FunctionalSpec extends Specification {
        def "run build"() {
            given:
            def dir = new File("/tmp/gradle-build")
            
            new File(dir, "build.gradle").text << """
                task helloWorld << { println 'Hello world!' }
            """
            
            when:
            def result = GradleRunnerFactory.create().with {
                directory = dir
                arguments << "helloWorld")
                run()
            }
            
            then:
            result.standardOutput.contains "Hello World!"
        }
    }

### Sad day cases

Things can go wrong with the tooling api, these would have to be appropriately presented to the user.

### Test coverage

1. A build can be run successfully
2. A failed build can be run successfully
3. Tooling API mechanical failures produce good diagnostic messages

### Implementation approach

1. The `GradleRunnerBuilder` and `GradleRunner` impls will be backed by the tooling API. 
2. The Gradle version to use will be hardcoded to `GradleVersion.current()`
3. No environmental control will be allowed (e.g. setting env vars or sys props)

## Story 3: A user functionally tests their custom build logic (from their IDE)

User's can roll their own functional test solution now, either using the Gradle Launcher or the Tooling API. The Gradle Launcher is effectively (but not officially) deprecated, and because the Tooling API forces the execution of Gradle in a separate VM it is not practical to use during development (i.e. tests cannot be run easily from the IDE). The “functional” aspect of the test kit will provide a way to use the Tooling API for launching Gradle builds (for the sake of testing) in an “IDE” compatible way. The problem with the combination of the Tooling API and the IDE is that the code under test is not available to the Tooling API. The classes in the test environment must somehow be injected into test build. 

### User visible changes

none.

### Sad day cases

1. The effective classpath for the code under test cannot be determined
3. The “injected classpath” may cause a deep gradle error on bootstrapping (can't think of how right now)

### Test coverage

1. Can successfully inject a standalone plugin class and apply it
    1. Via class object
    2. Via id (plugin.properties also injected)
2. Can successfully inject a plugin & task with dependencies
3. Injected classpaths do not “leak” across executions

### Implementation approach

The classpath to use will be determined by using a ClassLoaderClasspathSource, which is how we do the same thing with worker actions. Given this classpath, an init script will be written to `«project-dir»/.gradle-test-kit/init.gradle that applies it to all projects. For the Gradle execution, an implicit `-I «path-to-init-script»` will be _prepended_ to the argument list.

The classpath will not be available for user init scripts of settings files (possibly solved later), only build scripts.

There is no attempt to auto delete the .gradle-test-kit dir after test execution. The management of the directory to run in is not in scope, and will likely be handled by other mechanisms in the future (e.g. JUnit rule @TemporaryFolder).

## Story 4: A user functionally debugs their custom build logic

## Story 5: A user gets more precise information about what happened when the test build was executed

### Discussion

This will involve building out `ExecutionResult` to expose:

1. Success/failure
2. Tasks “run”, and information about them (e.g. skipped, up-to-date etc.)
3. Whether a task ran before another (for testing task dependencies)
4. Information about failures

## Story 6: A user functionally tests their custom build logic, with different and/or custom Gradle versions 

## Story 7: A user functionally tests their custom build logic, specifying JVM system props and env vars

## Story 8: A user functionally tests their custom build logic, simulating user input

## Story 9: A user integration tests their custom build logic

### Discussion

In the test-kit, integration tests will what we traditionally call a class of unit tests. These will be tests that insantiate plugins/tasks etc. with real collaborators. An example of such a test would be using `ProjectBuilder`. 

The integration test component of the test-kit will be conceptually similar to the functionality of HelperUtil. That is, many factories for creating test friendly versions of core types or mechanisms where objects such as tasks/projects can be instantiated as they would be during build configuration except that dependency injection can be influenced to _override_ what would normally be injected in order to provide test doubles.

It is unclear how much will be executable in this mode. For example it may not be possible to fully execute tasks, but it may be possible to simulate this in order to interrogate the incremental build API or task dependency chain.

# Open issues

None known at this time.
