
export interface UploadUserModelRequest {
  userId: string;
  modelName: string;
  displayName?: string;
  description?: string;
  modelType?: string;
  modelSubtype?: string;
  isPublic?: boolean;
}

export interface UpdateUserModelPublicRequest {
  userId: string;
  uploadId: string;
  isPublic: boolean;
}

export interface UserUploadedModelResponse {
  uploadId: string;
  userId: string;
  modelName: string;
  displayName?: string;
  description?: string;
  modelType?: string;
  modelSubtype?: string;
  modelFormat: string;
  modelUrl: string;
  previewUrl?: string;
  binUrl?: string;
  extraFiles?: string;
  fileSize?: number;
  status: string;
  isPublic: boolean;
  createdAt: string;
  updatedAt: string;
}