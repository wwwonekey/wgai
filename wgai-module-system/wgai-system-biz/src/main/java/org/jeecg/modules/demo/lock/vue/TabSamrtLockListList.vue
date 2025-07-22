<template>
  <a-card :bordered="false">
    <!-- 查询区域 -->
    <div class="table-page-search-wrapper">
      <a-form layout="inline" @keyup.enter.native="searchQuery">
        <a-row :gutter="24">
        </a-row>
      </a-form>
    </div>
    <!-- 查询区域-END -->

    <!-- 操作按钮区域 -->
    <div class="table-operator">
      <a-button @click="handleAdd" type="primary" icon="plus">新增</a-button>
      <a-button type="primary" icon="download" @click="handleExportXls('智能锁列表')">导出</a-button>
      <a-upload name="file" :showUploadList="false" :multiple="false" :headers="tokenHeader" :action="importExcelUrl" @change="handleImportExcel">
        <a-button type="primary" icon="import">导入</a-button>
      </a-upload>
      <!-- 高级查询区域 -->
      <j-super-query :fieldList="superFieldList" ref="superQueryModal" @handleSuperQuery="handleSuperQuery"></j-super-query>
      <a-dropdown v-if="selectedRowKeys.length > 0">
        <a-menu slot="overlay">
          <a-menu-item key="1" @click="batchDel"><a-icon type="delete"/>删除</a-menu-item>
        </a-menu>
        <a-button style="margin-left: 8px"> 批量操作 <a-icon type="down" /></a-button>
      </a-dropdown>
    </div>

    <!-- table区域-begin -->
    <div>
      <div class="ant-alert ant-alert-info" style="margin-bottom: 16px;">
        <i class="anticon anticon-info-circle ant-alert-icon"></i> 已选择 <a style="font-weight: 600">{{ selectedRowKeys.length }}</a>项
        <a style="margin-left: 24px" @click="onClearSelected">清空</a>
      </div>

      <a-table
        ref="table"
        size="middle"
        :scroll="{x:true}"
        bordered
        rowKey="id"
        :columns="columns"
        :dataSource="dataSource"
        :pagination="ipagination"
        :loading="loading"
        :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
        class="j-table-force-nowrap"
        @change="handleTableChange">

        <template slot="htmlSlot" slot-scope="text">
          <div v-html="text"></div>
        </template>
        <template slot="imgSlot" slot-scope="text,record">
          <span v-if="!text" style="font-size: 12px;font-style: italic;">无图片</span>
          <img v-else :src="getImgView(text)" :preview="record.id" height="25px" alt="" style="max-width:80px;font-size: 12px;font-style: italic;"/>
        </template>
        <template slot="fileSlot" slot-scope="text">
          <span v-if="!text" style="font-size: 12px;font-style: italic;">无文件</span>
          <a-button
            v-else
            :ghost="true"
            type="primary"
            icon="download"
            size="small"
            @click="downloadFile(text)">
            下载
          </a-button>
        </template>

        <span slot="action" slot-scope="text, record">
          <a @click="handleEdit(record)">编辑</a>

          <a-divider type="vertical" />
          <a-dropdown>
            <a class="ant-dropdown-link">更多 <a-icon type="down" /></a>
            <a-menu slot="overlay">
              <a-menu-item>
                <a @click="handleDetail(record)">详情</a>
              </a-menu-item>
              <a-menu-item>
                <a-popconfirm title="确定删除吗?" @confirm="() => handleDelete(record.id)">
                  <a>删除</a>
                </a-popconfirm>
              </a-menu-item>
            </a-menu>
          </a-dropdown>
        </span>

      </a-table>
    </div>

    <tab-samrt-lock-list-modal ref="modalForm" @ok="modalFormOk"></tab-samrt-lock-list-modal>
  </a-card>
</template>

<script>

  import '@/assets/less/TableExpand.less'
  import { mixinDevice } from '@/utils/mixin'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import TabSamrtLockListModal from './modules/TabSamrtLockListModal'

  export default {
    name: 'TabSamrtLockListList',
    mixins:[JeecgListMixin, mixinDevice],
    components: {
      TabSamrtLockListModal
    },
    data () {
      return {
        description: '智能锁列表管理页面',
        // 表头
        columns: [
          {
            title: '#',
            dataIndex: '',
            key:'rowIndex',
            width:60,
            align:"center",
            customRender:function (t,r,index) {
              return parseInt(index)+1;
            }
          },
          {
            title:'廒间名称',
            align:"center",
            dataIndex: 'wareId_dictText'
          },
          {
            title:'锁具编码',
            align:"center",
            dataIndex: 'lockUid'
          },
          {
            title:'锁具IMEI',
            align:"center",
            dataIndex: 'lockImei'
          },
          {
            title:'锁具名称',
            align:"center",
            dataIndex: 'lockName'
          },
          {
            title:'锁具地址',
            align:"center",
            dataIndex: 'lockAddress'
          },
          {
            title:'锁具经度',
            align:"center",
            dataIndex: 'lockLng'
          },
          {
            title:'锁具纬度',
            align:"center",
            dataIndex: 'lockLag'
          },
          {
            title:'锁具协议类型',
            align:"center",
            dataIndex: 'lockIotType'
          },
          {
            title:'设备地址(ip+端口)',
            align:"center",
            dataIndex: 'lockIp'
          },
          {
            title:'用户名',
            align:"center",
            dataIndex: 'lockUsername'
          },
          {
            title:'设备密码',
            align:"center",
            dataIndex: 'lockPassword'
          },
          {
            title:'TOPIC',
            align:"center",
            dataIndex: 'lockTopic'
          },
          {
            title: '操作',
            dataIndex: 'action',
            align:"center",
            fixed:"right",
            width:147,
            scopedSlots: { customRender: 'action' }
          }
        ],
        url: {
          list: "/lock/tabSamrtLockList/list",
          delete: "/lock/tabSamrtLockList/delete",
          deleteBatch: "/lock/tabSamrtLockList/deleteBatch",
          exportXlsUrl: "/lock/tabSamrtLockList/exportXls",
          importExcelUrl: "lock/tabSamrtLockList/importExcel",
          
        },
        dictOptions:{},
        superFieldList:[],
      }
    },
    created() {
    this.getSuperFieldList();
    },
    computed: {
      importExcelUrl: function(){
        return `${window._CONFIG['domianURL']}/${this.url.importExcelUrl}`;
      },
    },
    methods: {
      initDictConfig(){
      },
      getSuperFieldList(){
        let fieldList=[];
        fieldList.push({type:'sel_search',value:'wareId',text:'廒间名称',dictTable:"", dictText:'', dictCode:''})
        fieldList.push({type:'string',value:'lockUid',text:'锁具编码',dictCode:''})
        fieldList.push({type:'string',value:'lockImei',text:'锁具IMEI',dictCode:''})
        fieldList.push({type:'string',value:'lockName',text:'锁具名称',dictCode:''})
        fieldList.push({type:'string',value:'lockAddress',text:'锁具地址',dictCode:''})
        fieldList.push({type:'string',value:'lockLng',text:'锁具经度',dictCode:''})
        fieldList.push({type:'string',value:'lockLag',text:'锁具纬度',dictCode:''})
        fieldList.push({type:'string',value:'lockIotType',text:'锁具协议类型',dictCode:''})
        fieldList.push({type:'string',value:'lockIp',text:'设备地址(ip+端口)',dictCode:''})
        fieldList.push({type:'string',value:'lockUsername',text:'用户名',dictCode:''})
        fieldList.push({type:'string',value:'lockPassword',text:'设备密码',dictCode:''})
        fieldList.push({type:'string',value:'lockTopic',text:'TOPIC',dictCode:''})
        this.superFieldList = fieldList
      }
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less';
</style>