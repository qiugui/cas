import {AfterViewInit, Component, OnInit, ViewChild} from '@angular/core';
import {ServiceItem} from "../../domain/service-view-bean";
import {Messages} from "../messages";
import {ActivatedRoute, Router} from "@angular/router";
import {ServiceViewService} from "./service.service";
import {Location} from "@angular/common";
import {MatDialog, MatPaginator, MatSnackBar} from "@angular/material";
import {DeleteComponent} from "../delete/delete.component";
import {ControlsService} from "../controls/controls.service";
import {Database, Datasource} from "../database";

@Component({
  selector: 'app-services',
  templateUrl: './services.component.html',
  styleUrls: ['./services.component.css']
})
export class ServicesComponent implements OnInit,AfterViewInit {
  deleteItem: ServiceItem;
  domain: String;
  selectedItem: ServiceItem;
  revertItem: ServiceItem;
  serviceDatabase: Database<ServiceItem> = new Database<ServiceItem>();
  dataSource: Datasource<ServiceItem> | null;
  displayedColumns = ['actions','name','serviceId','description'];

  @ViewChild("paginator")
  paginator: MatPaginator;

  constructor(public messages: Messages,
              private route: ActivatedRoute,
              private router: Router,
              private service: ServiceViewService,
              private location: Location,
              public dialog: MatDialog,
              public snackBar: MatSnackBar,
              public controlsService: ControlsService) {
  }

  ngOnInit() {
    this.dataSource = new Datasource(this.serviceDatabase,this.paginator);
    this.route.data
      .subscribe((data: { resp: ServiceItem[]}) => {
        if (!data.resp) {
          this.snackBar.open(this.messages.management_services_status_listfail,'dismiss',{
            duration: 5000
          });
        }
        this.serviceDatabase.load(data.resp);
      });
    this.route.params.subscribe((params) => this.domain = params['domain']);
  }

  ngAfterViewInit() {

  }

  serviceEdit(item?: ServiceItem) {
    if (item) {
      this.selectedItem = item;
    }
    this.router.navigate(['/form',this.selectedItem.assignedId]);
  }

  serviceDuplicate() {
    this.router.navigate(['/duplicate',this.selectedItem.assignedId]);
  }

  openModalDelete() {
    let dialogRef = this.dialog.open(DeleteComponent,{
      data: this.selectedItem,
      width: '500px',
      position: {top: '100px'}
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.delete();
      }
    });
    this.deleteItem = this.selectedItem;
  };

  delete() {
    let myData = {id: this.deleteItem.assignedId};

    this.service.delete(Number.parseInt(this.deleteItem.assignedId as string))
      .then(resp => this.handleDelete(resp))
      .catch((e: any) => this.snackBar.open(e.message || e.text(), 'Dismiss', {
        duration: 5000
      }));
  };

  handleDelete(name: String) {
    this.snackBar.open(name+" "+this.messages.management_services_status_deleted,'Dismiss', {
      duration: 5000
    });
    this.refresh();
  }

  refresh() {
    this.getServices();
  }

  getServices() {
    this.service.getServices(this.domain)
      .then(resp => this.serviceDatabase.load(resp))
      .catch((e: any) => this.snackBar.open(this.messages.management_services_status_listfail,'Dismiss', {
        duration: 5000
      }));
  }

  moveUp(a: ServiceItem) {
    let index: number = this.serviceDatabase.data.indexOf(a);
    if(index > 0) {
      let b: ServiceItem = this.serviceDatabase.data[index - 1];
      a.evalOrder = index-1;
      b.evalOrder = index;
      this.service.updateOrder(a,b).then(resp => this.refresh());
    }
  }

  moveDown(a: ServiceItem) {
    let index: number = this.serviceDatabase.data.indexOf(a);
    if(index < this.serviceDatabase.data.length -1) {
      let b: ServiceItem = this.serviceDatabase.data[index + 1];
      a.evalOrder = index+1;
      b.evalOrder = index;
      this.service.updateOrder(a,b).then(resp => this.refresh());
    }
  }

  showMoveUp(): boolean {
    if (!this.selectedItem) {
      return false;
    }
    let index = this.serviceDatabase.data.indexOf(this.selectedItem);
    return index > 0;
  }

  showMoveDown(): boolean {
    if (!this.selectedItem) {
      return false;
    }
    let index = this.serviceDatabase.data.indexOf(this.selectedItem);
    return index < this.serviceDatabase.data.length - 1;
  }
}
