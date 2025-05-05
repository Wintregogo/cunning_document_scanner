import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

import 'ios_options.dart';

export 'ios_options.dart';

class CunningDocumentScanner {
  static const MethodChannel _channel =
      MethodChannel('cunning_document_scanner');

  /// Call this to start get Picture workflow.
  /// 
  /// - [noOfPages] 限制要扫描的页数，默认为1，只扫描一页
  /// - [isGalleryImportAllowed] 是否允许从相册中选择图片，默认为false
  /// - [iosScannerOptions] iOS特有的扫描选项，用于配置图像格式和压缩质量
  ///
  /// 该方法会启动一个文档扫描流程，使用边缘检测功能帮助用户定位文档。
  /// 
  /// 在Android平台上：
  /// - 用户拍照后自动检测文档边缘
  /// - 检测到边缘后自动接受并返回裁剪后的图像
  /// - 整个流程只需一次点击即可完成
  ///
  /// 在iOS平台上：
  /// - 用户需要手动点击"Save"按钮保存扫描结果
  /// - 这是iOS VisionKit API的限制，无法像Android那样完全自动化
  /// - 即使用户扫描了多页，也只会返回第一页的结果
  static Future<List<String>?> getPictures({
    int noOfPages = 1,
    bool isGalleryImportAllowed = false,
    IosScannerOptions? iosScannerOptions,
  }) async {
    Map<Permission, PermissionStatus> statuses = await [
      Permission.camera,
    ].request();
    if (statuses.containsValue(PermissionStatus.denied) ||
        statuses.containsValue(PermissionStatus.permanentlyDenied)) {
      throw Exception("Permission not granted");
    }

    final List<dynamic>? pictures = await _channel.invokeMethod('getPictures', {
      'noOfPages': noOfPages,
      'isGalleryImportAllowed': isGalleryImportAllowed,
      if (iosScannerOptions != null)
        'iosScannerOptions': {
          'imageFormat': iosScannerOptions.imageFormat.name,
          'jpgCompressionQuality': iosScannerOptions.jpgCompressionQuality,
        }
    });
    return pictures?.map((e) => e as String).toList();
  }
}
