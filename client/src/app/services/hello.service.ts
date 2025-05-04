import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";

@Injectable({
  providedIn: "root",
})
export class HelloService {
  private baseUrl = "http://localhost:8080";

  constructor(private http: HttpClient) {}

  getHello(): Observable<string> {
    return this.http.get(`${this.baseUrl}/hello`, { responseType: "text" });
  }
}
