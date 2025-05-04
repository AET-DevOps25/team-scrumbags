import { Component } from "@angular/core";
import { RouterOutlet } from "@angular/router";
import { HelloService } from "./services/hello.service";

@Component({
  selector: "app-root",
  imports: [RouterOutlet],
  templateUrl: "./app.component.html",
  styleUrls: ["./app.component.scss"],
})
export class AppComponent {
  title = "client";

  message = "Loading...";

  constructor(private helloService: HelloService) {}

  ngOnInit(): void {
    this.helloService.getHello().subscribe({
      next: (data) => (this.message = data),
      error: () => (this.message = "Error fetching message"),
    });
  }
}
