import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { environment } from "../../environments/environment";
import { Runtime } from "./api.model";

@Injectable({
  providedIn: "root",
})
export class ApiService {
  constructor(private http: HttpClient) {}

  getRuntime() {
    return this.http.get<Runtime>(environment.baseUrl + "/api/runtime");
  }
}
