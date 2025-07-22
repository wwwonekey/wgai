<template>
  <a-spin :spinning="confirmLoading">
    <j-form-container :disabled="formDisabled">
      <a-form-model ref="form" :model="model" :rules="validatorRules" slot="detail">
        <a-row>
          <a-col :span="24">
            <a-form-model-item label="廒间名称" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="wareId">
              <j-search-select-tag v-model="model.wareId" dict=""  />
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="锁具编码" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockUid">
              <a-input v-model="model.lockUid" placeholder="请输入锁具编码"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="锁具IMEI" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockImei">
              <a-input v-model="model.lockImei" placeholder="请输入锁具IMEI"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="锁具名称" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockName">
              <a-input v-model="model.lockName" placeholder="请输入锁具名称"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="锁具地址" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockAddress">
              <a-input v-model="model.lockAddress" placeholder="请输入锁具地址"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="锁具经度" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockLng">
              <a-input v-model="model.lockLng" placeholder="请输入锁具经度"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="锁具纬度" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockLag">
              <a-input v-model="model.lockLag" placeholder="请输入锁具纬度"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="锁具协议类型" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockIotType">
              <a-input v-model="model.lockIotType" placeholder="请输入锁具协议类型"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="设备地址(ip+端口)" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockIp">
              <a-input v-model="model.lockIp" placeholder="请输入设备地址(ip+端口)"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="用户名" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockUsername">
              <a-input v-model="model.lockUsername" placeholder="请输入用户名"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="设备密码" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockPassword">
              <a-input v-model="model.lockPassword" placeholder="请输入设备密码"  ></a-input>
            </a-form-model-item>
          </a-col>
          <a-col :span="24">
            <a-form-model-item label="TOPIC" :labelCol="labelCol" :wrapperCol="wrapperCol" prop="lockTopic">
              <a-input v-model="model.lockTopic" placeholder="请输入TOPIC"  ></a-input>
            </a-form-model-item>
          </a-col>
        </a-row>
      </a-form-model>
    </j-form-container>
  </a-spin>
</template>

<script>

  import { httpAction, getAction } from '@/api/manage'
  import { validateDuplicateValue } from '@/utils/util'

  export default {
    name: 'TabSamrtLockListForm',
    components: {
    },
    props: {
      //表单禁用
      disabled: {
        type: Boolean,
        default: false,
        required: false
      }
    },
    data () {
      return {
        model:{
         },
        labelCol: {
          xs: { span: 24 },
          sm: { span: 5 },
        },
        wrapperCol: {
          xs: { span: 24 },
          sm: { span: 16 },
        },
        confirmLoading: false,
        validatorRules: {
        },
        url: {
          add: "/lock/tabSamrtLockList/add",
          edit: "/lock/tabSamrtLockList/edit",
          queryById: "/lock/tabSamrtLockList/queryById"
        }
      }
    },
    computed: {
      formDisabled(){
        return this.disabled
      },
    },
    created () {
       //备份model原始值
      this.modelDefault = JSON.parse(JSON.stringify(this.model));
    },
    methods: {
      add () {
        this.edit(this.modelDefault);
      },
      edit (record) {
        this.model = Object.assign({}, record);
        this.visible = true;
      },
      submitForm () {
        const that = this;
        // 触发表单验证
        this.$refs.form.validate(valid => {
          if (valid) {
            that.confirmLoading = true;
            let httpurl = '';
            let method = '';
            if(!this.model.id){
              httpurl+=this.url.add;
              method = 'post';
            }else{
              httpurl+=this.url.edit;
               method = 'put';
            }
            httpAction(httpurl,this.model,method).then((res)=>{
              if(res.success){
                that.$message.success(res.message);
                that.$emit('ok');
              }else{
                that.$message.warning(res.message);
              }
            }).finally(() => {
              that.confirmLoading = false;
            })
          }
         
        })
      },
    }
  }
</script>