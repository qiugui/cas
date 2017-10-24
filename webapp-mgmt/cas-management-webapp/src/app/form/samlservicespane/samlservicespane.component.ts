import {Component, Input, OnInit} from '@angular/core';
import {Messages} from "../../messages";
import {SamlRegisteredService} from "../../../domain/saml-service";
import {Data} from "../data";

import {DataSource} from "@angular/cdk/table";
import {BehaviorSubject} from "rxjs/BehaviorSubject";
import {Observable} from "rxjs/Observable";
import 'rxjs/add/operator/startWith';
import 'rxjs/add/observable/merge';
import 'rxjs/add/operator/map';
import {Util} from "../../util/util";

@Component({
  selector: 'app-samlservicespane',
  templateUrl: './samlservicespane.component.html',
  styleUrls: ['./samlservicespane.component.css']
})
export class SamlservicespaneComponent implements OnInit {

  selectOptions;
  displayedColumns = ['source', 'mapped', "delete"];
  attributeDatabase = new AttributeDatabase();
  dataSource: AttributeDataSource | null;

  type: String;

  constructor(public messages: Messages,
              public data: Data) {
    this.selectOptions = data.selectOptions;
  }

  ngOnInit() {
    let service: SamlRegisteredService = this.data.service as SamlRegisteredService;

    if (Util.isEmpty(service.attributeNameFormats)) {
      service.attributeNameFormats = new Map();
    }
    for (let p of Array.from(Object.keys(service.attributeNameFormats))) {
      this.attributeDatabase.addRow(new Row(p));
    }
    this.dataSource = new AttributeDataSource(this.attributeDatabase);
  }

  addRow(){
    this.attributeDatabase.addRow(new Row(""));
  }

  doChange(row: Row, val: string) {
    let service: SamlRegisteredService = this.data.service as SamlRegisteredService;
    service.attributeNameFormats[val] = service.attributeNameFormats[row.key as string];
    delete service.attributeNameFormats[row.key as string];
    row.key = val;
  }

  delete(row: Row) {
    let service: SamlRegisteredService = this.data.service as SamlRegisteredService
    delete service.attributeNameFormats[row.key as string];
    this.attributeDatabase.removeRow(row);
  }
}

export class Row {
  key: String;

  constructor(source: String) {
    this.key = source;
  }
}

export class AttributeDatabase {
  dataChange: BehaviorSubject<Row[]> = new BehaviorSubject<Row[]>([]);
  get data(): Row[] { return this.dataChange.value; }

  constructor() {
  }

  addRow(row: Row) {
    const copiedData = this.data.slice();
    copiedData.push(row);
    this.dataChange.next(copiedData);
  }

  removeRow(row: Row) {
    const copiedData = this.data.slice();
    copiedData.splice(copiedData.indexOf(row),1);
    this.dataChange.next(copiedData);
  }
}

export class AttributeDataSource extends DataSource<any> {
  constructor(private _attributeDatabase: AttributeDatabase) {
    super();
  }

  connect(): Observable<Row[]> {
    return this._attributeDatabase.dataChange;
  }

  disconnect() {}
}
