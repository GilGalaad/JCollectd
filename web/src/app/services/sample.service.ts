import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { environment } from "../../environments/environment";
import { Runtime } from "./sample.model";

@Injectable({
  providedIn: "root",
})
export class SampleService {
  constructor(private http: HttpClient) {}

  getRuntime() {
    return this.http.get<Runtime>(environment.baseUrl + "/api/runtime");
  }

  getDataset(probeNumber: number) {
    return this.http.get<[][]>(environment.baseUrl + "/api/probe/" + probeNumber + "/dataset");
  }
}
