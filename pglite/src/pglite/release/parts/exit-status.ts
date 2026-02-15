export class ExitStatus {
  name = "ExitStatus";
  message: string;
  status: number;

  constructor(status: number) {
    this.message = `Program terminated with exit(${status})`;
    this.status = status;
  }
}
