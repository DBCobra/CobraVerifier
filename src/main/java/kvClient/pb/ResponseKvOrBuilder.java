// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: txn.proto

package kvClient.pb;

public interface ResponseKvOrBuilder extends
    // @@protoc_insertion_point(interface_extends:pb.ResponseKv)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>int64 txnid = 1;</code>
   */
  long getTxnid();

  /**
   * <code>.pb.KeyValue kv = 2;</code>
   */
  boolean hasKv();
  /**
   * <code>.pb.KeyValue kv = 2;</code>
   */
  kvClient.pb.KeyValue getKv();
  /**
   * <code>.pb.KeyValue kv = 2;</code>
   */
  kvClient.pb.KeyValueOrBuilder getKvOrBuilder();

  /**
   * <code>.pb.Key k = 3;</code>
   */
  boolean hasK();
  /**
   * <code>.pb.Key k = 3;</code>
   */
  kvClient.pb.Key getK();
  /**
   * <code>.pb.Key k = 3;</code>
   */
  kvClient.pb.KeyOrBuilder getKOrBuilder();

  /**
   * <code>.pb.ServiceError e = 4;</code>
   */
  boolean hasE();
  /**
   * <code>.pb.ServiceError e = 4;</code>
   */
  kvClient.pb.ServiceError getE();
  /**
   * <code>.pb.ServiceError e = 4;</code>
   */
  kvClient.pb.ServiceErrorOrBuilder getEOrBuilder();

  public kvClient.pb.ResponseKv.ResultCase getResultCase();
}