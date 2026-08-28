// Used only by frontend/Dockerfile's build (docker compose --profile full up), via the
// "docker-compose" configuration in angular.json. Same optimization settings as a real
// production build, but apiUrl stays localhost:8080 - the backend container publishes that
// port to the host, and it's the *browser* (not the nginx container) that calls the API, so it
// can't resolve Docker-internal service names anyway. See frontend/Dockerfile's comment.
export const environment = {
  production: true,
  apiUrl: 'http://localhost:8080'
};
