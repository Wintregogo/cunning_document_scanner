import Flutter
import UIKit
import Vision
import VisionKit
import AVFoundation

@available(iOS 13.0, *)
public class SwiftCunningDocumentScannerPlugin: NSObject, FlutterPlugin, VNDocumentCameraViewControllerDelegate {
  var resultChannel: FlutterResult?
  var presentingController: VNDocumentCameraViewController?
  var scannerOptions: CunningScannerOptions = CunningScannerOptions()
  // 增加一个标记，用于跟踪是否已经处理过结果
  private var hasProcessedResult = false
  // 提示标签视图
  private var overlayView: UIView?

  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "cunning_document_scanner", binaryMessenger: registrar.messenger())
    let instance = SwiftCunningDocumentScannerPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    if call.method == "getPictures" {
        scannerOptions = CunningScannerOptions.fromArguments(args: call.arguments)
        
        // 获取当前窗口和根控制器（iOS 13+ 兼容方式）
        var presentedVC: UIViewController?
        if #available(iOS 15.0, *) {
            presentedVC = UIApplication.shared.connectedScenes
                .filter { $0.activationState == .foregroundActive }
                .first(where: { $0 is UIWindowScene })
                .flatMap { $0 as? UIWindowScene }?.windows
                .first(where: { $0.isKeyWindow })?.rootViewController
        } else if #available(iOS 13.0, *) {
            presentedVC = UIApplication.shared.windows.first(where: { $0.isKeyWindow })?.rootViewController
        } else {
            // 旧版本 iOS（不会执行到这里，因为我们已经要求 iOS 13.0 以上）
            presentedVC = UIApplication.shared.keyWindow?.rootViewController
        }
        
        guard let presentedVC = presentedVC else {
            result(FlutterError(code: "NO_VIEW", message: "No view controller found", details: nil))
            return
        }
        
        self.resultChannel = result
        self.hasProcessedResult = false
        
        if VNDocumentCameraViewController.isSupported {
            self.presentingController = VNDocumentCameraViewController()
            self.presentingController!.delegate = self
            
            // 设置导航标题
            if let navController = self.presentingController?.navigationController {
                navController.navigationBar.topItem?.title = "请调整位置，拍照后按save"
            }
            
            // 呈现扫描界面
            presentedVC.present(self.presentingController!, animated: true) { [weak self] in
                // 添加自定义覆盖层
                self?.addCustomOverlay()
                
                // 尝试在延迟后隐藏闪光灯按钮 (注意：这是尝试性的，可能无法在所有iOS版本上都有效)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self?.hideFlashButton()
                }
            }
        } else {
            result(FlutterError(code: "UNAVAILABLE", message: "Document camera is not available on this device", details: nil))
        }
    } else {
        result(FlutterMethodNotImplemented)
        return
    }
  }
  
  private func hideFlashButton() {
    guard let controller = self.presentingController else { return }
    
    // 遍历视图层次结构查找闪光灯按钮
    func findFlashButton(in view: UIView) {
        for subview in view.subviews {
            // 尝试寻找可能是闪光灯按钮的视图
            if let button = subview as? UIButton {
                // 检查按钮是否在屏幕底部中间位置
                let centerX = button.center.x
                let screenWidth = UIScreen.main.bounds.width
                
                // 如果按钮在屏幕中间区域（假设闪光灯按钮在中间）
                if centerX > screenWidth * 0.4 && centerX < screenWidth * 0.6 {
                    // 隐藏按钮
                    button.isHidden = true
                    return
                }
            }
            
            // 递归检查子视图
            findFlashButton(in: subview)
        }
    }
    
    findFlashButton(in: controller.view)
  }
  
  private func addCustomOverlay() {
     DispatchQueue.main.async { [weak self] in
         guard let self = self, let controller = self.presentingController else { return }
         
         // 创建顶部覆盖层
         let topBackground = UIView()
         topBackground.backgroundColor = UIColor.black.withAlphaComponent(0.8)
         topBackground.translatesAutoresizingMaskIntoConstraints = false
         
         // 创建顶部提示标签
         let topLabel = UILabel()
         topLabel.text = "系统会自动拍照\n拍照后点击Save按钮保存"
         topLabel.textAlignment = .center
         topLabel.textColor = .white
         topLabel.font = UIFont.systemFont(ofSize: 18, weight: .bold)
         topLabel.numberOfLines = 0
         topLabel.translatesAutoresizingMaskIntoConstraints = false
         
         // 创建中央提示标签 (覆盖系统的"Position the document in view")
         let centerLabel = UILabel()
         centerLabel.text = "自动拍照后点Save"
         centerLabel.textAlignment = .center
         centerLabel.textColor = .white
         centerLabel.backgroundColor = UIColor.black.withAlphaComponent(0.7)
         centerLabel.font = UIFont.systemFont(ofSize: 22, weight: .bold)
         centerLabel.numberOfLines = 0
         centerLabel.layer.cornerRadius = 12
         centerLabel.layer.masksToBounds = true
         centerLabel.translatesAutoresizingMaskIntoConstraints = false
         
         // 创建闪烁动画以吸引注意
         centerLabel.alpha = 0.8
         
         // 添加到视图层次结构
         controller.view.addSubview(topBackground)
         topBackground.addSubview(topLabel)
         controller.view.addSubview(centerLabel)
         
         // 设置约束 - 顶部背景
         NSLayoutConstraint.activate([
             topBackground.topAnchor.constraint(equalTo: controller.view.topAnchor),
             topBackground.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor),
             topBackground.trailingAnchor.constraint(equalTo: controller.view.trailingAnchor),
             topBackground.heightAnchor.constraint(equalToConstant: 120) // 足够高覆盖状态栏和导航栏
         ])
         
         // 设置约束 - 顶部标签
         NSLayoutConstraint.activate([
             topLabel.centerXAnchor.constraint(equalTo: topBackground.centerXAnchor),
             topLabel.bottomAnchor.constraint(equalTo: topBackground.bottomAnchor, constant: -20),
             topLabel.leadingAnchor.constraint(greaterThanOrEqualTo: topBackground.leadingAnchor, constant: 20),
             topLabel.trailingAnchor.constraint(lessThanOrEqualTo: topBackground.trailingAnchor, constant: -20)
         ])
         
         // 设置约束 - 中心标签
         NSLayoutConstraint.activate([
             centerLabel.centerXAnchor.constraint(equalTo: controller.view.centerXAnchor),
             centerLabel.centerYAnchor.constraint(equalTo: controller.view.centerYAnchor, constant: -80),
             centerLabel.leadingAnchor.constraint(greaterThanOrEqualTo: controller.view.leadingAnchor, constant: 40),
             centerLabel.trailingAnchor.constraint(lessThanOrEqualTo: controller.view.trailingAnchor, constant: -40),
             centerLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 60)
         ])
         
         // 添加内边距
         let padding: CGFloat = 16
         centerLabel.layoutMargins = UIEdgeInsets(top: padding, left: padding, bottom: padding, right: padding)
         
         // 创建闪烁动画以吸引用户注意
         UIView.animate(withDuration: 1.0, delay: 0, options: [.repeat, .autoreverse], animations: {
             centerLabel.alpha = 1.0
         }, completion: nil)
         
         // 存储引用以便清理
         let container = UIView()
         container.addSubview(topBackground)
         container.addSubview(centerLabel)
         self.overlayView = container
     }
  }

  func getDocumentsDirectory() -> URL {
      let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
      let documentsDirectory = paths[0]
      return documentsDirectory
  }

  public func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
      // 避免重复处理
      if hasProcessedResult {
          return
      }
      hasProcessedResult = true
      
      // 移除覆盖层
      overlayView?.removeFromSuperview()
      
      let tempDirPath = self.getDocumentsDirectory()
      let currentDateTime = Date()
      let df = DateFormatter()
      df.dateFormat = "yyyyMMdd-HHmmss"
      let formattedDate = df.string(from: currentDateTime)
      var filenames: [String] = []
      
      // 只处理第一页，忽略其余页面
      if scan.pageCount > 0 {
          // 使用最后一张照片，这通常是质量最好的一张
          let page = scan.imageOfPage(at: scan.pageCount - 1)
          let url = tempDirPath.appendingPathComponent(formattedDate + ".\(scannerOptions.imageFormat.rawValue)")
          switch scannerOptions.imageFormat {
          case CunningScannerImageFormat.jpg:
              try? page.jpegData(compressionQuality: scannerOptions.jpgCompressionQuality)?.write(to: url)
          case CunningScannerImageFormat.png:
              try? page.pngData()?.write(to: url)
          }
          
          filenames.append(url.path)
      }
      
      // 无论用户选择了多少页，我们只返回最后一页
      resultChannel?(filenames)
      presentingController?.dismiss(animated: true)
  }

  public func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
      // 避免重复处理
      if hasProcessedResult {
          return
      }
      hasProcessedResult = true
      
      // 移除覆盖层
      overlayView?.removeFromSuperview()
      
      resultChannel?(nil)
      presentingController?.dismiss(animated: true)
  }

  public func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
      // 避免重复处理
      if hasProcessedResult {
          return
      }
      hasProcessedResult = true
      
      // 移除覆盖层
      overlayView?.removeFromSuperview()
      
      resultChannel?(FlutterError(code: "ERROR", message: error.localizedDescription, details: nil))
      presentingController?.dismiss(animated: true)
  }
}
