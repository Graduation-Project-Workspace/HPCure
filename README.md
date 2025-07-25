# HPCure - Breast MRI Analysis for Android

<img width="798" height="173" alt="Screenshot 2025-07-25 at 7 19 39 pm" src="https://github.com/user-attachments/assets/0957be48-129c-4bf3-82ac-63dbb9abfde9" />


**HPCure** is an Android application for medical imaging analysis, focused on processing **breast MRI scans**. It leverages advanced machine learning models and image processing techniques to assist in tumor segmentation and volume estimation, supporting medical diagnosis and research.

---

## 🚀 Features

- **MRI Tumor Segmentation**
  - Uses TensorFlow Lite models and fuzzy connectedness algorithms.
  
- **Tumor Volume Estimation**
  - Automatically calculates volume based on segmented regions.

- **Dual Computation Modes**
  - **Parallel Mode:** Tasks like seed prediction, ROI detection, and segmentation run simultaneously for faster processing on multi-core devices.
  - **Serial Mode:** Tasks are executed one by one — ideal for low-resource environments or deterministic processing.

---

## 🧠 Machine Learning Models

- **ROI Detection** and **Seed Prediction** are powered by custom-trained models using **YOLOv8**.
- These models were trained on labeled breast MRI datasets.
- The trained models were then exported to **TensorFlow Lite (.tflite)** format for seamless integration into the Android app.

---

## ⚙️ Workflow

### 1. Input
- Upload or select MRI DICOM files or binary image slices through the app interface.

### 2. Processing
- **YOLOv8-derived TFLite models** are used for seed and ROI prediction.
- **Fuzzy Connectedness** is applied to perform image segmentation.
- User selects between **parallel** and **serial** modes.

### 3. Output
- Results are accessible through the app’s interface. The output files are stored in the app’s internal storage and are not directly accessible via the device’s file manager.

---

## 📂 Documentation

For detailed technical and architectural information, refer to the [`docs/`](./docs/) directory.

---

## 🔗 Related Repositories

- **gRPC Communication Layer**  
  HPCure communicates with backend services using [gRPC-Android-Demo](https://github.com/mernaislam/gRPC_Android_Demo).

---

## 📱 Tech Stack

| Component         | Technology           |
|------------------|----------------------|
| Platform         | Android              |
| ML Inference     | TensorFlow Lite (TFLite) |
| ML Training      | YOLOv8               |
| Image Processing | Fuzzy Connectedness  |
| Communication    | gRPC                 |

---
