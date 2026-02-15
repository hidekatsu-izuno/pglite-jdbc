export class ExitStatus {
  name = "ExitStatus";

  constructor(status) {
    this.message = `Program terminated with exit(${status})`;
    this.status = status;
  }
}
