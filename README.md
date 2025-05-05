# Cunning Document Scanner (Customized Version)

这是cunning_document_scanner的自定义版本，专门为简化发票扫描流程而修改。

原始项目：[cunning_document_scanner](https://github.com/jachzen/cunning_document_scanner)

## 主要修改内容

- **简化扫描流程**：提供更流畅的文档扫描体验
- **优化边缘检测**：保持原有的边缘检测功能，但简化用户交互
- **默认单页模式**：默认只扫描一页，针对发票场景优化
- **平台优化**：针对Android和iOS平台提供各自优化的体验

## 平台差异说明

### Android
- 完全自动化流程：用户只需点击一次拍照按钮
- 自动检测文档边缘并短暂显示
- 自动接受检测到的边缘并返回裁剪后的图像
- 整个过程无需用户额外交互

### iOS
- 利用iOS原生VisionKit进行文档扫描
- 用户需要点击"Save"按钮保存扫描结果
- 即使扫描多页，插件也只会返回第一页
- 由于iOS API限制，无法实现完全一致的自动化体验

## 使用方法

```dart
import 'package:cunning_document_scanner/cunning_document_scanner.dart';

// 启动扫描流程
final imagesPath = await CunningDocumentScanner.getPictures();

// 高级配置选项
final imagesPath = await CunningDocumentScanner.getPictures(
  noOfPages: 1, // 设置页数限制为1页
  isGalleryImportAllowed: false, // 不允许从相册选择图片
  iosScannerOptions: Platform.isIOS ? IosScannerOptions(
    imageFormat: IosImageFormat.jpg,
    jpgCompressionQuality: 0.8, // 80%的质量，平衡文件大小和图像质量
  ) : null,
);
```

## 扫描流程

1. 打开相机，立即进入预览模式
2. 实时进行边缘检测，帮助用户定位文档
3. 用户拍照
4. 处理文档并返回裁剪后的图像路径

## 平台支持

- Android: 使用Google ML Kit的文档扫描功能
- iOS: 使用VisionKit的文档扫描功能

## 安装

在pubspec.yaml中添加依赖:

```yaml
dependencies:
  cunning_document_scanner:
    path: ./cunning_document_scanner  # 如果在项目根目录下
    # 或者使用相对路径
    # path: ../cunning_document_scanner  # 如果在子项目中
```

## 开源许可

此项目基于 MIT 许可证。详见 [LICENSE](LICENSE) 文件。