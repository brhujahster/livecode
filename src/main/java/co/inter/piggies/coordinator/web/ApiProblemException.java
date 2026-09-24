package co.inter.piggies.coordinator.web;

class ApiProblemException extends RuntimeException {

    private final ApiProblem problem;

    ApiProblemException(ApiProblem problem) {
        super(problem.detail());
        this.problem = problem;
    }

    ApiProblem problem() {
        return problem;
    }
}
