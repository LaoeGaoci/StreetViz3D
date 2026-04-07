export interface UserInfo {
    userId: string;
    userName: string;
    avatarUrl: string;
    email: string;
    createdAt: string;
    updatedAt: string;
}

export interface UpdateAvatarRequest {
    userId: string;
    avatarUrl: string;
}

export interface UpdatePasswordRequest {
    userId: string;
    oldPassword: string;
    newPassword: string;
}
export interface UserStreetItem {
    streetId: string;
    streetName: string;
    width: number;
    createdAt: string;
    updatedAt: string;
}